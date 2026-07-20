package com.ppgpt.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppgpt.gateway.adapter.AiProviderAdapterFactory;
import com.ppgpt.gateway.domain.ChatLog;
import com.ppgpt.gateway.domain.Model;
import com.ppgpt.gateway.domain.UserGroup;
import com.ppgpt.gateway.dto.ChatRequest;
import com.ppgpt.gateway.dto.ModelDto;
import com.ppgpt.gateway.repository.ChatLogRepository;
import com.ppgpt.gateway.repository.CreditRateRepository;
import com.ppgpt.gateway.repository.GroupModelAccessRepository;
import com.ppgpt.gateway.repository.ModelRepository;
import com.ppgpt.gateway.repository.UserGroupRepository;
import com.ppgpt.gateway.repository.UserRepository;
import com.ppgpt.gateway.util.TokenizerUtil;
import com.ppgpt.gateway.event.TokenUsageRecordedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Core AI streaming service.
 *
 * <p>
 * Flow:
 * <ol>
 * <li>Validate model access (group_model_access check)</li>
 * <li>Estimate input tokens → pre-check quota in Redis</li>
 * <li>Resolve the correct {@link com.ppgpt.gateway.adapter.AiProviderAdapter}
 * for the model's provider</li>
 * <li>Delegate streaming to the adapter (OpenAI / Azure / Bedrock …)</li>
 * <li>doFinally: count actual tokens, correct credit deduction, save ChatLog
 * (async)</li>
 * </ol>
 *
 * <p>
 * <strong>Important:</strong> token counting, credit deduction, and chat-log
 * persistence
 * remain in this class and wrap all adapters uniformly.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final AiProviderAdapterFactory adapterFactory;
    private final ModelRepository modelRepository;
    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    private final GroupModelAccessRepository groupModelAccessRepository;
    private final CreditRateRepository creditRateRepository;
    private final ChatLogRepository chatLogRepository;
    private final CryptoService cryptoService;
    private final QuotaService quotaService;
    private final TokenizerUtil tokenizerUtil;
    private final ObjectMapper objectMapper;
    private final R2dbcEntityTemplate entityTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    // ─── Models ───────────────────────────────────────────────────────────────

    public Flux<ModelDto> getAvailableModels(String userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")))
                .flatMapMany(user -> groupModelAccessRepository.findByGroupId(user.getGroupId()))
                .flatMap(access -> modelRepository.findById(access.getModelId()))
                .filter(Model::isActive)
                .filter(m -> "GENERATION".equals(m.getModelType()))
                .map(m -> ModelDto.builder()
                        .id(m.getId())
                        .name(m.getName())
                        .provider(m.getProvider())
                        .modelName(m.getModelName())
                        .endpointUrl(m.getEndpointUrl())
                        .isActive(m.isActive())
                        .build());
    }

    // ─── Streaming chat ───────────────────────────────────────────────────────

    public Flux<ServerSentEvent<String>> streamChat(String userId, ChatRequest request) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")))
                .flatMap(user -> userGroupRepository.findById(user.getGroupId()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "User has no group")))
                .flatMap(group -> modelRepository.findById(request.getModelId())
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found")))
                        .flatMap(model ->
                        // Verify group has access to this model
                        groupModelAccessRepository.existsByGroupIdAndModelId(group.getId(), model.getId())
                                .filter(Boolean::booleanValue)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(
                                        HttpStatus.FORBIDDEN, "Your group does not have access to this model")))
                                .flatMap(ok ->
                                // Load credit rate
                                creditRateRepository.findByModelId(model.getId())
                                        .defaultIfEmpty(defaultRate(model.getId()))
                                        .flatMap(rate -> {
                                            // Estimate input tokens (JTokkit with char-fallback)
                                            int inputTokens = tokenizerUtil.countTokens(
                                                    model.getModelName(), request.getMessage());
                                            BigDecimal estimatedCredits = BigDecimal.valueOf(inputTokens)
                                                    .multiply(rate.getInputMultiplier())
                                                    .setScale(4, RoundingMode.HALF_UP);

                                            // Atomic quota pre-check + reservation
                                            return quotaService.checkAndReserve(userId, group, estimatedCredits)
                                                    .filter(Boolean::booleanValue)
                                                    .switchIfEmpty(Mono.error(new ResponseStatusException(
                                                            HttpStatus.TOO_MANY_REQUESTS,
                                                            "Daily quota exceeded. Limit: "
                                                                    + group.getMaxDailyCredits())))
                                                    .thenReturn(Map.of(
                                                            "model", model,
                                                            "inputTokens", inputTokens,
                                                            "estimatedCredits", estimatedCredits,
                                                            "inMult", rate.getInputMultiplier(),
                                                            "outMult", rate.getOutputMultiplier()));
                                        })))
                        .map(ctx -> Map.entry(group, ctx)))
                .flatMapMany(entry -> {
                    UserGroup group = (UserGroup) entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ctx = (Map<String, Object>) entry.getValue();
                    Model model = (Model) ctx.get("model");
                    int inputTokens = (int) ctx.get("inputTokens");
                    BigDecimal estimated = (BigDecimal) ctx.get("estimatedCredits");
                    BigDecimal inMult = (BigDecimal) ctx.get("inMult");
                    BigDecimal outMult = (BigDecimal) ctx.get("outMult");

                    Mono<Boolean> guardrailCheck = Mono.just(true); // Default: proceed

                    if (group.getGuardrailModelId() != null && !group.getGuardrailModelId().isBlank()) {
                        guardrailCheck = modelRepository.findById(group.getGuardrailModelId())
                                .flatMap(guardrailModel -> {
                                    // Deduct guardrail tokens later if needed, for simplicity we just block or
                                    // proceed
                                    String guardrailPrompt = "Evaluate the following user prompt for safety. Respond with only 'SAFE' or 'UNSAFE'. Prompt: "
                                            + request.getMessage();

                                    ChatRequest gReq = new ChatRequest();
                                    gReq.setMessage(guardrailPrompt);
                                    gReq.setModelId(guardrailModel.getId());
                                    gReq.setSessionId(request.getSessionId());

                                    String gCreds = cryptoService.decrypt(guardrailModel.getCredentialsEncrypted());
                                    return adapterFactory.resolve(guardrailModel.getProvider())
                                            .streamChat(gReq, guardrailModel, gCreds)
                                            .collectList()
                                            .map(chunks -> {
                                                String fullResp = String.join("", chunks).toUpperCase();
                                                if (fullResp.contains("UNSAFE")) {
                                                    log.warn("Guardrail triggered for user {}. Blocked request.",
                                                            userId);
                                                    return false; // Not safe
                                                }
                                                return true; // Safe
                                            })
                                            .onErrorResume(ex -> {
                                                log.error("Guardrail check failed: {}", ex.getMessage());
                                                return Mono.just(true); // Fail-open (or fail-close based on policy)
                                            });
                                });
                    }

                    return guardrailCheck.flatMapMany(isSafe -> {
                        if (!isSafe) {
                            // Refund the estimated generation credits by finalizing to 0
                            quotaService.finalizeDeduction(userId, estimated, BigDecimal.ZERO)
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .subscribe();

                            return Mono.just(ServerSentEvent.<String>builder()
                                    .data(buildChunk("Policy Violation: Request blocked by safety guardrail.", true))
                                    .build());
                        }

                        // Decrypt credentials (CPU-only, non-blocking)
                        String decryptedCredentials = cryptoService.decrypt(model.getCredentialsEncrypted());

                        // ── Slice history to max_history_messages ────────────────────
                        List<Map<String, String>> rawHistory = request.getHistory() != null ? request.getHistory()
                                : Collections.emptyList();
                        // maxHistoryMessages in the model actually represents conversation TURNS (1
                        // turn = user + assistant = 2 messages)
                        int maxHistoryMessages = model.getMaxHistoryMessages() * 2;
                        List<Map<String, String>> slicedHistory = rawHistory.size() <= maxHistoryMessages
                                ? rawHistory
                                : rawHistory.subList(rawHistory.size() - maxHistoryMessages, rawHistory.size());

                        // Build a new request with sliced history so adapters can use it
                        request.setHistory(slicedHistory);

                        // Accumulate streamed response for token counting
                        AtomicReference<StringBuilder> responseAccumulator = new AtomicReference<>(new StringBuilder());
                        AtomicBoolean finalized = new AtomicBoolean(false);

                        // Metric tracking: start timestamp
                        long startTime = System.currentTimeMillis();

                        // ── Delegate to provider-specific adapter ─────────────────────
                        return adapterFactory.resolve(model.getProvider())
                                .streamChat(request, model, decryptedCredentials)
                                .timeout(Duration.ofMillis(model.getTimeoutMs()))
                                .onErrorResume(TimeoutException.class, ex -> {
                                    log.warn("[{}] Request timed out after {}ms", model.getProvider(),
                                            model.getTimeoutMs());
                                    return Flux.just("[Request timed out after " + model.getTimeoutMs() + "ms]");
                                })
                                .doOnNext(contentFragment -> {
                                    if (contentFragment != null && !contentFragment.isEmpty()) {
                                        responseAccumulator.get().append(contentFragment);
                                    }
                                })
                                .map(contentFragment -> buildChunk(contentFragment, false))
                                .concatWith(Mono.just(buildChunk("", true))) // terminal done event
                                // ── Token counting + credit correction (fires on complete, cancel, error) ──
                                .doFinally(signalType -> {
                                    if (finalized.compareAndSet(false, true)) {
                                        long durationMs = System.currentTimeMillis() - startTime;
                                        meterRegistry.timer("ai.gateway.chat.latency", "provider", model.getProvider())
                                                .record(Duration.ofMillis(durationMs));
                                        meterRegistry.counter("ai.gateway.chat.requests", "provider", model.getProvider(), "status", signalType.name())
                                                .increment();

                                        String fullResponse = responseAccumulator.get().toString();
                                        int outputTokens = tokenizerUtil.countTokens(model.getModelName(),
                                                fullResponse);
                                        BigDecimal actualCredits = BigDecimal.valueOf(inputTokens).multiply(inMult)
                                                .add(BigDecimal.valueOf(outputTokens).multiply(outMult))
                                                .setScale(4, RoundingMode.HALF_UP);

                                        log.debug("Chat finalized [{}] provider={}. in={} out={} credits={}",
                                                signalType, model.getProvider(), inputTokens, outputTokens,
                                                actualCredits);

                                        // Decoupled Event-Driven: Publish token usage event for C-Level Dashboard
                                        // Analytics
                                        eventPublisher.publishEvent(new TokenUsageRecordedEvent(
                                                group.getId(),
                                                model.getId(),
                                                inputTokens,
                                                outputTokens));

                                        // Async: correct Redis deduction + persist to DB
                                        quotaService.finalizeDeduction(userId, estimated, actualCredits)
                                                .then(saveChatLog(userId, request, model, fullResponse))
                                                .subscribeOn(Schedulers.boundedElastic())
                                                .subscribe(
                                                        null,
                                                        err -> log.error("Post-stream finalization failed: {}",
                                                                err.getMessage()));
                                    }
                                })
                                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());
                    }); // End of guardrail flatMapMany
                });
    }

    // ─── Chat history ─────────────────────────────────────────────────────────

    public Flux<ChatLog> getChatHistory(String userId, int page, int size) {
        return chatLogRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                org.springframework.data.domain.PageRequest.of(page, size));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String buildChunk(String content, boolean done) {
        try {
            return objectMapper.writeValueAsString(Map.of("content", content, "done", done));
        } catch (JsonProcessingException e) {
            return "{\"content\":\"\",\"done\":" + done + "}";
        }
    }

    private Mono<ChatLog> saveChatLog(String userId, ChatRequest request, Model model, String response) {
        String displayName = (model.getName() != null && !model.getName().isBlank())
                ? model.getName()
                : model.getModelName();

        ChatLog log = ChatLog.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .modelId(model.getId())
                .modelDisplayName(displayName)
                .sessionId(request.getSessionId())
                .prompt(request.getMessage())
                .response(response)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        return entityTemplate.insert(log);
    }

    private com.ppgpt.gateway.domain.CreditRate defaultRate(String modelId) {
        return com.ppgpt.gateway.domain.CreditRate.builder()
                .modelId(modelId)
                .inputMultiplier(BigDecimal.ONE)
                .outputMultiplier(BigDecimal.valueOf(2))
                .build();
    }
}

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        private final McpServerService mcpServerService;

        // ─── Models ───────────────────────────────────────────────────────────────

        public Flux<ModelDto> getAvailableModels(String userId) {
                return userRepository.findById(userId)
                                .switchIfEmpty(Mono.error(
                                                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")))
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
                                                .supportsVision(m.isSupportsVision())
                                                .supportsTools(m.isSupportsTools())
                                                .build());
        }

        // ─── Streaming chat ───────────────────────────────────────────────────────

        public Flux<ServerSentEvent<String>> streamChat(String userId, ChatRequest request) {
                return userRepository.findById(userId)
                                .switchIfEmpty(Mono.error(
                                                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")))
                                .flatMap(user -> userGroupRepository.findById(user.getGroupId()))
                                .switchIfEmpty(Mono.error(
                                                new ResponseStatusException(HttpStatus.FORBIDDEN, "User has no group")))
                                .flatMap(group -> modelRepository.findById(request.getModelId())
                                                .switchIfEmpty(Mono.error(new ResponseStatusException(
                                                                HttpStatus.NOT_FOUND, "Model not found")))
                                                .flatMap(model -> {
                                                        // Pre-flight check: Image attachments validation
                                                        if (request.getImages() != null
                                                                        && !request.getImages().isEmpty()
                                                                        && !model.isSupportsVision()) {
                                                                return Mono.error(new ResponseStatusException(
                                                                                HttpStatus.BAD_REQUEST,
                                                                                "Model '" + (model.getName() != null
                                                                                                && !model.getName()
                                                                                                                .isBlank() ? model
                                                                                                                                .getName()
                                                                                                                                : model.getModelName())
                                                                                                + "' does not support image or file attachments"));
                                                        }

                                                        // Verify group has access to this model
                                                        return groupModelAccessRepository
                                                                        .existsByGroupIdAndModelId(group.getId(),
                                                                                        model.getId())
                                                                        .filter(Boolean::booleanValue)
                                                                        .switchIfEmpty(Mono.error(
                                                                                        new ResponseStatusException(
                                                                                                        HttpStatus.FORBIDDEN,
                                                                                                        "Your group does not have access to this model")))
                                                                        .flatMap(ok ->
                                                        // Load credit rate
                                                        creditRateRepository.findByModelId(model.getId())
                                                                        .defaultIfEmpty(defaultRate(model.getId()))
                                                                        .flatMap(rate -> {
                                                                                // Estimate input tokens (JTokkit with
                                                                                // char-fallback)
                                                                                int inputTokens = tokenizerUtil
                                                                                                .countTokens(
                                                                                                                model.getModelName(),
                                                                                                                request.getMessage());
                                                                                BigDecimal estimatedCredits = BigDecimal
                                                                                                .valueOf(inputTokens)
                                                                                                .multiply(rate.getInputMultiplier())
                                                                                                .setScale(4, RoundingMode.HALF_UP);

                                                                                // Atomic quota pre-check + reservation
                                                                                return quotaService.checkAndReserve(
                                                                                                userId, group,
                                                                                                estimatedCredits)
                                                                                                .filter(Boolean::booleanValue)
                                                                                                .switchIfEmpty(Mono
                                                                                                                .error(new ResponseStatusException(
                                                                                                                                HttpStatus.TOO_MANY_REQUESTS,
                                                                                                                                "Daily quota exceeded. Limit: "
                                                                                                                                                + group.getMaxDailyCredits())))
                                                                                                .thenReturn(Map.of(
                                                                                                                "model",
                                                                                                                model,
                                                                                                                "inputTokens",
                                                                                                                inputTokens,
                                                                                                                "estimatedCredits",
                                                                                                                estimatedCredits,
                                                                                                                "inMult",
                                                                                                                rate.getInputMultiplier(),
                                                                                                                "outMult",
                                                                                                                rate.getOutputMultiplier()));
                                                                        }));
                                                })
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

                                        if (group.getGuardrailModelId() != null
                                                        && !group.getGuardrailModelId().isBlank()) {
                                                guardrailCheck = modelRepository.findById(group.getGuardrailModelId())
                                                                .flatMap(guardrailModel -> {
                                                                        // Deduct guardrail tokens later if needed, for
                                                                        // simplicity we just block or
                                                                        // proceed
                                                                        String guardrailPrompt = "Evaluate the following user prompt for safety. Respond with only 'SAFE' or 'UNSAFE'. Prompt: "
                                                                                        + request.getMessage();

                                                                        ChatRequest gReq = new ChatRequest();
                                                                        gReq.setMessage(guardrailPrompt);
                                                                        gReq.setModelId(guardrailModel.getId());
                                                                        gReq.setSessionId(request.getSessionId());

                                                                        String gCreds = cryptoService.decrypt(
                                                                                        guardrailModel.getCredentialsEncrypted());
                                                                        return adapterFactory
                                                                                        .resolve(guardrailModel
                                                                                                        .getProvider())
                                                                                        .streamChat(gReq,
                                                                                                        guardrailModel,
                                                                                                        gCreds)
                                                                                        .collectList()
                                                                                        .map(chunks -> {
                                                                                                String fullResp = String
                                                                                                                .join("", chunks)
                                                                                                                .toUpperCase();
                                                                                                if (fullResp.contains(
                                                                                                                "UNSAFE")) {
                                                                                                        log.warn("Guardrail triggered for user {}. Blocked request.",
                                                                                                                        userId);
                                                                                                        return false; // Not
                                                                                                                      // safe
                                                                                                }
                                                                                                return true; // Safe
                                                                                        })
                                                                                        .onErrorResume(ex -> {
                                                                                                log.error("Guardrail check failed: {}",
                                                                                                                ex.getMessage());
                                                                                                return Mono.just(true); // Fail-open
                                                                                                                        // (or
                                                                                                                        // fail-close
                                                                                                                        // based
                                                                                                                        // on
                                                                                                                        // policy)
                                                                                        });
                                                                });
                                        }

                                        return guardrailCheck.flatMapMany(isSafe -> {
                                                if (!isSafe) {
                                                        // Refund the estimated generation credits by finalizing to 0
                                                        quotaService.finalizeDeduction(userId, estimated,
                                                                        BigDecimal.ZERO)
                                                                        .subscribeOn(Schedulers.boundedElastic())
                                                                        .subscribe();

                                                        return Mono.just(ServerSentEvent.<String>builder()
                                                                        .data(buildChunk(
                                                                                        "Policy Violation: Request blocked by safety guardrail.",
                                                                                        true))
                                                                        .build());
                                                }

                                                // Decrypt credentials (CPU-only, non-blocking)
                                                String decryptedCredentials = cryptoService
                                                                .decrypt(model.getCredentialsEncrypted());

                                                // ── Slice history to max_history_messages ────────────────────
                                                List<Map<String, Object>> rawHistory = request.getHistory() != null
                                                                ? request.getHistory()
                                                                : Collections.emptyList();
                                                // maxHistoryMessages in the model actually represents conversation
                                                // TURNS (1
                                                // turn = user + assistant = 2 messages)
                                                int maxHistoryMessages = model.getMaxHistoryMessages() * 2;
                                                List<Map<String, Object>> slicedHistory = rawHistory
                                                                .size() <= maxHistoryMessages
                                                                                ? rawHistory
                                                                                : rawHistory.subList(rawHistory.size()
                                                                                                - maxHistoryMessages,
                                                                                                rawHistory.size());

                                                // Build a new request with sliced history so adapters can use it
                                                request.setHistory(slicedHistory);

                                                // Accumulate streamed response for token counting
                                                AtomicReference<StringBuilder> responseAccumulator = new AtomicReference<>(
                                                                new StringBuilder());
                                                AtomicBoolean finalized = new AtomicBoolean(false);

                                                // Metric tracking: start timestamp
                                                long startTime = System.currentTimeMillis();

                                                // ── Delegate to provider-specific adapter ─────────────────────
                                                Mono<List<com.ppgpt.gateway.dto.ToolDto>> toolsMono = (model.isSupportsTools())
                                                                ? ((request.getTools() != null && !request.getTools().isEmpty())
                                                                                ? Mono.just(request.getTools())
                                                                                : mcpServerService.getActiveTools().collectList())
                                                                : Mono.just(Collections.emptyList());

                                                return toolsMono.flatMapMany(activeTools -> {
                                                        request.setTools(activeTools);
                                                        if (activeTools != null && !activeTools.isEmpty()) {
                                                                return executeAgenticToolLoop(request, model,
                                                                                decryptedCredentials);
                                                        }
                                                        return adapterFactory.resolve(model.getProvider())
                                                                        .streamChat(request, model,
                                                                                        decryptedCredentials);
                                                })
                                                                .timeout(Duration.ofMillis(model.getTimeoutMs()))
                                                                .onErrorResume(TimeoutException.class, ex -> {
                                                                        log.warn("[{}] Request timed out after {}ms",
                                                                                        model.getProvider(),
                                                                                        model.getTimeoutMs());
                                                                        return Flux.just("[Request timed out after "
                                                                                        + model.getTimeoutMs() + "ms]");
                                                                })
                                                                .doOnNext(contentFragment -> {
                                                                        if (contentFragment != null
                                                                                        && !contentFragment.isEmpty()) {
                                                                                responseAccumulator.get().append(
                                                                                                contentFragment);
                                                                        }
                                                                })
                                                                .map(contentFragment -> buildChunk(contentFragment,
                                                                                false))
                                                                .concatWith(Mono.just(buildChunk("", true))) // terminal
                                                                                                             // done
                                                                                                             // event
                                                                // ── Token counting + credit correction (fires on
                                                                // complete, cancel, error) ──
                                                                .doFinally(signalType -> {
                                                                        if (finalized.compareAndSet(false, true)) {
                                                                                long durationMs = System
                                                                                                .currentTimeMillis()
                                                                                                - startTime;
                                                                                meterRegistry.timer(
                                                                                                "ai.gateway.chat.latency",
                                                                                                "provider",
                                                                                                model.getProvider())
                                                                                                .record(Duration.ofMillis(
                                                                                                                durationMs));
                                                                                meterRegistry.counter(
                                                                                                "ai.gateway.chat.requests",
                                                                                                "provider",
                                                                                                model.getProvider(),
                                                                                                "status",
                                                                                                signalType.name())
                                                                                                .increment();

                                                                                String fullResponse = responseAccumulator
                                                                                                .get().toString();
                                                                                int outputTokens = tokenizerUtil
                                                                                                .countTokens(model
                                                                                                                .getModelName(),
                                                                                                                fullResponse);
                                                                                BigDecimal actualCredits = BigDecimal
                                                                                                .valueOf(inputTokens)
                                                                                                .multiply(inMult)
                                                                                                .add(BigDecimal.valueOf(
                                                                                                                outputTokens)
                                                                                                                .multiply(outMult))
                                                                                                .setScale(4, RoundingMode.HALF_UP);

                                                                                log.debug("Chat finalized [{}] provider={}. in={} out={} credits={}",
                                                                                                signalType,
                                                                                                model.getProvider(),
                                                                                                inputTokens,
                                                                                                outputTokens,
                                                                                                actualCredits);

                                                                                // Decoupled Event-Driven: Publish token
                                                                                // usage event for C-Level Dashboard
                                                                                // Analytics
                                                                                eventPublisher.publishEvent(
                                                                                                new TokenUsageRecordedEvent(
                                                                                                                group.getId(),
                                                                                                                model.getId(),
                                                                                                                inputTokens,
                                                                                                                outputTokens));

                                                                                // Async: correct Redis deduction +
                                                                                // persist to DB
                                                                                quotaService.finalizeDeduction(userId,
                                                                                                estimated,
                                                                                                actualCredits)
                                                                                                .then(saveChatLog(
                                                                                                                userId,
                                                                                                                request,
                                                                                                                model,
                                                                                                                fullResponse))
                                                                                                .subscribeOn(Schedulers
                                                                                                                .boundedElastic())
                                                                                                .subscribe(
                                                                                                                null,
                                                                                                                err -> log.error(
                                                                                                                                "Post-stream finalization failed: {}",
                                                                                                                                err.getMessage()));
                                                                        }
                                                                })
                                                                .map(chunk -> ServerSentEvent.<String>builder()
                                                                                .data(chunk).build());
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

        @SuppressWarnings("unchecked")
        private Flux<String> executeAgenticToolLoop(ChatRequest request, Model model, String decryptedCredentials) {
                AtomicReference<StringBuilder> toolCallAcc = new AtomicReference<>(new StringBuilder());

                Flux<String> pass1Flux = adapterFactory.resolve(model.getProvider())
                                .streamChat(request, model, decryptedCredentials)
                                .flatMap(fragment -> {
                                        if (fragment == null || fragment.isEmpty()) {
                                                return Mono.empty();
                                        }
                                        if (fragment.contains("tool_calls")) {
                                                toolCallAcc.get().append(fragment);
                                                return Mono.empty(); // Suppress tool call JSON fragment from UI stream
                                        }
                                        return Mono.just(fragment); // Stream live text fragment directly to UI
                                });

                return pass1Flux.concatWith(Flux.defer(() -> {
                        String fullToolCallStr = toolCallAcc.get().toString();

                        if (fullToolCallStr.contains("tool_calls")) {
                                log.info("[ChatService] Tool calls detected in 1st pass. Executing agentic tool loop...");

                                List<Map<String, Object>> toolCalls = parseToolCallsFromStream(fullToolCallStr);
                                if (toolCalls != null && !toolCalls.isEmpty()) {
                                        return Flux.fromIterable(toolCalls)
                                                        .concatMap(tc -> {
                                                                String callId = (String) tc.get("id");
                                                                Map<String, Object> func = (Map<String, Object>) tc.get("function");
                                                                String name = (String) func.get("name");
                                                                Object rawArgs = func.get("arguments");
                                                                Map<String, Object> argsMap = Collections.emptyMap();
                                                                if (rawArgs instanceof Map) {
                                                                        argsMap = (Map<String, Object>) rawArgs;
                                                                } else if (rawArgs instanceof String && !((String) rawArgs).isBlank()) {
                                                                        try {
                                                                                argsMap = objectMapper.readValue((String) rawArgs, Map.class);
                                                                        } catch (Exception ignored) {}
                                                                }

                                                                log.info("[Agentic] Executing tool {} with args {}", name, argsMap);
                                                                return mcpServerService.executeTool(name, argsMap)
                                                                                .map(toolResult -> Map.<String, Object>of(
                                                                                                "role", "tool",
                                                                                                "tool_call_id", callId != null ? callId : "call_1",
                                                                                                "name", name,
                                                                                                "content", toolResult
                                                                                ));
                                                        })
                                                        .collectList()
                                                        .flatMapMany(toolResults -> {
                                                                ChatRequest pass2Req = new ChatRequest();
                                                                pass2Req.setModelId(request.getModelId());
                                                                pass2Req.setMessage(null);
                                                                pass2Req.setSessionId(request.getSessionId());
                                                                pass2Req.setImages(request.getImages());
                                                                pass2Req.setTools(Collections.emptyList());

                                                                List<Map<String, Object>> history = new ArrayList<>();
                                                                if (request.getHistory() != null) {
                                                                        history.addAll(request.getHistory());
                                                                }
                                                                if (request.getMessage() != null && !request.getMessage().isBlank()) {
                                                                        history.add(Map.of("role", "user", "content", request.getMessage()));
                                                                }
                                                                history.add(Map.of("role", "assistant", "content", "", "tool_calls", toolCalls));
                                                                history.addAll(toolResults);

                                                                pass2Req.setHistory(history);

                                                                log.info("[Agentic] Sending 2nd pass request to model with tool execution results...");
                                                                return adapterFactory.resolve(model.getProvider())
                                                                                .streamChat(pass2Req, model, decryptedCredentials)
                                                                                .filter(fragment -> fragment == null || !fragment.contains("tool_calls"))
                                                                                .defaultIfEmpty("[ขออภัยครับ ระบบไม่สามารถประมวลผลคำตอบจากเครื่องมือได้ในขณะนี้]");
                                                        });
                                }
                        }

                        return Flux.empty();
                }))
                .onErrorResume(ex -> {
                        log.warn("[Agentic] Tool execution or model request failed with error: {}. Retrying without tools...", ex.getMessage());
                        ChatRequest fallbackReq = new ChatRequest();
                        fallbackReq.setModelId(request.getModelId());
                        fallbackReq.setMessage(request.getMessage());
                        fallbackReq.setSessionId(request.getSessionId());
                        fallbackReq.setImages(request.getImages());
                        fallbackReq.setHistory(request.getHistory());
                        fallbackReq.setTools(Collections.emptyList());

                        return adapterFactory.resolve(model.getProvider())
                                        .streamChat(fallbackReq, model, decryptedCredentials);
                });
        }

        @SuppressWarnings("unchecked")
        private List<Map<String, Object>> parseToolCallsFromStream(String rawStreamText) {
                if (rawStreamText == null || rawStreamText.isBlank()) return Collections.emptyList();
                Map<Integer, Map<String, Object>> toolMap = new LinkedHashMap<>();
                String trimmedText = rawStreamText.trim();

                try {
                        // Case 1: Direct JSON object string containing "tool_calls" (e.g. {"tool_calls": [...]})
                        if (trimmedText.startsWith("{") && trimmedText.contains("\"tool_calls\"")) {
                                try {
                                        Map<String, Object> directObj = objectMapper.readValue(trimmedText, Map.class);
                                        if (directObj.containsKey("tool_calls") && directObj.get("tool_calls") instanceof List) {
                                                List<Map<String, Object>> tcList = (List<Map<String, Object>>) directObj.get("tool_calls");
                                                for (Map<String, Object> tc : tcList) {
                                                        if (tc != null && tc.containsKey("function")) {
                                                                String callId = tc.get("id") != null ? tc.get("id").toString() : "call_1";
                                                                Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                                                                String name = fn.get("name") != null ? fn.get("name").toString() : "";
                                                                Object args = fn.get("arguments");
                                                                Map<String, Object> toolObj = new LinkedHashMap<>();
                                                                toolObj.put("id", callId);
                                                                toolObj.put("type", "function");
                                                                toolObj.put("function", Map.of("name", name, "arguments", args != null ? args : ""));
                                                                toolMap.put(toolMap.size(), toolObj);
                                                        }
                                                }
                                                if (!toolMap.isEmpty()) {
                                                        return new ArrayList<>(toolMap.values());
                                                }
                                        }
                                } catch (Exception ignored) {
                                }
                        }

                        // Case 2: Regex matching for streaming SSE chunks containing {"content":..., "tool_calls": [...]} or {"tool_calls": [...]}
                        Matcher m = Pattern.compile("(\\{.*?\"tool_calls\":\\[.*?\\]\\})")
                                        .matcher(rawStreamText);
                        while (m.find()) {
                                String jsonStr = m.group(1);
                                try {
                                        Map<String, Object> chunk = objectMapper.readValue(jsonStr, Map.class);
                                        List<Map<String, Object>> tcList = (List<Map<String, Object>>) chunk.get("tool_calls");
                                        if (tcList != null) {
                                                for (Map<String, Object> tc : tcList) {
                                                        int idx = tc.containsKey("index")
                                                                        ? ((Number) tc.get("index")).intValue()
                                                                        : 0;
                                                        Map<String, Object> tool = toolMap.computeIfAbsent(idx, k -> {
                                                                Map<String, Object> t = new LinkedHashMap<>();
                                                                t.put("id", tc.get("id") != null ? tc.get("id") : "call_1");
                                                                t.put("type", "function");
                                                                Map<String, Object> fn = new LinkedHashMap<>();
                                                                fn.put("name", "");
                                                                fn.put("arguments", "");
                                                                t.put("function", fn);
                                                                return t;
                                                        });

                                                        if (tc.get("id") != null)
                                                                tool.put("id", tc.get("id"));
                                                        Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                                                        if (fn != null) {
                                                                Map<String, Object> targetFn = (Map<String, Object>) tool
                                                                                .get("function");
                                                                if (fn.get("name") != null
                                                                                && !fn.get("name").toString().isBlank()) {
                                                                        targetFn.put("name", fn.get("name"));
                                                                }
                                                                if (fn.get("arguments") != null) {
                                                                        String currentArgs = (String) targetFn.get("arguments");
                                                                        targetFn.put("arguments", currentArgs
                                                                                        + fn.get("arguments").toString());
                                                                }
                                                        }
                                                }
                                        }
                                } catch (Exception ignored) {
                                }
                        }
                } catch (Exception e) {
                        log.warn("Failed to parse tool calls from stream: {}", e.getMessage());
                }
                return new ArrayList<>(toolMap.values());
        }
}

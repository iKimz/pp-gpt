package com.ppgpt.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppgpt.gateway.adapter.AiProviderAdapterFactory;
import com.ppgpt.gateway.domain.ChatLog;
import com.ppgpt.gateway.domain.CreditRate;
import com.ppgpt.gateway.domain.Model;
import com.ppgpt.gateway.domain.UserGroup;
import com.ppgpt.gateway.dto.ChatRequest;
import com.ppgpt.gateway.dto.ModelDto;
import com.ppgpt.gateway.dto.ToolDto;
import com.ppgpt.gateway.event.TokenUsageRecordedEvent;
import com.ppgpt.gateway.repository.ChatLogRepository;
import com.ppgpt.gateway.repository.CreditRateRepository;
import com.ppgpt.gateway.repository.GroupModelAccessRepository;
import com.ppgpt.gateway.repository.ModelRepository;
import com.ppgpt.gateway.repository.UserGroupRepository;
import com.ppgpt.gateway.repository.UserRepository;
import com.ppgpt.gateway.util.JsonUtil;
import com.ppgpt.gateway.util.TokenizerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
 * Core AI Chat Streaming & Agentic Tool Execution Service.
 *
 * <p>Execution Flow:</p>
 * <ol>
 *   <li>Validates model access against authenticated user's group permissions.</li>
 *   <li>Estimates input tokens and performs atomic pre-flight quota check in Redis.</li>
 *   <li>Evaluates safety guardrail models if configured for the user group.</li>
 *   <li>Executes Agentic Tool Loop (Pass 1: Tool execution, Pass 2: Answer synthesis) if tools are enabled.</li>
 *   <li>Delegates streaming completion to provider-specific adapters (OpenAI, Azure, AWS Bedrock).</li>
 *   <li>Performs post-stream token counting, credit deduction correction, and async audit log persistence.</li>
 * </ol>
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

    /**
     * Retrieves models available for invocation by the authenticated user.
     *
     * @param userId Authenticated user ID
     * @return Flux of available model DTOs
     */
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
                        .supportsVision(m.isSupportsVision())
                        .supportsTools(m.isSupportsTools())
                        .build())
                .sort((a, b) -> {
                    String nameA = (a.getName() != null && !a.getName().isBlank()) ? a.getName() : (a.getModelName() != null ? a.getModelName() : "");
                    String nameB = (b.getName() != null && !b.getName().isBlank()) ? b.getName() : (b.getModelName() != null ? b.getModelName() : "");
                    return nameA.compareToIgnoreCase(nameB);
                });
    }

    /**
     * Streams an AI chat completion response via Server-Sent Events (SSE).
     *
     * @param userId  Authenticated user ID
     * @param request Chat completion request
     * @return Flux of SSE string events
     */
    public Flux<ServerSentEvent<String>> streamChat(String userId, ChatRequest request) {
        if (request.getModelId() == null || request.getModelId().isBlank()) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "modelId is required"));
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required"));
        }

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")))
                .flatMapMany(user -> processUserChat(userId, user.getGroupId(), request));
    }

    private Flux<ServerSentEvent<String>> processUserChat(String userId, String groupId, ChatRequest request) {
        return groupModelAccessRepository.existsByGroupIdAndModelId(groupId, request.getModelId())
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied for model: " + request.getModelId())))
                .flatMapMany(hasAccess -> modelRepository.findById(request.getModelId())
                        .filter(Model::isActive)
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found or inactive")))
                        .flatMapMany(model -> userGroupRepository.findById(groupId)
                                .flatMapMany(group -> creditRateRepository.findByModelId(model.getId())
                                        .defaultIfEmpty(defaultRate(model.getId()))
                                        .flatMapMany(creditRate -> executeStreamPipeline(userId, group, model, creditRate, request))
                                )
                        )
                );
    }

    private Flux<ServerSentEvent<String>> executeStreamPipeline(String userId, UserGroup group, Model model, CreditRate creditRate, ChatRequest request) {
        int inputTokens = tokenizerUtil.countTokens(model.getModelName(), request.getMessage());
        BigDecimal inMult = creditRate.getInputMultiplier();
        BigDecimal outMult = creditRate.getOutputMultiplier();

        BigDecimal estimated = BigDecimal.valueOf(inputTokens)
                .multiply(inMult)
                .add(BigDecimal.valueOf(1000).multiply(outMult))
                .setScale(4, RoundingMode.HALF_UP);

        return quotaService.checkAndReserveQuota(userId, group.getMaxDailyCredits(), estimated)
                .flatMapMany(allowed -> {
                    if (!allowed) {
                        return Flux.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Daily credit quota exceeded. Reset occurs daily."));
                    }

                    Mono<Boolean> guardrailCheck = (group.getGuardrailModelId() != null && !group.getGuardrailModelId().isBlank())
                            ? evaluateGuardrail(group.getGuardrailModelId(), request.getMessage())
                            : Mono.just(true);

                    return guardrailCheck.flatMapMany(isSafe -> {
                        if (!isSafe) {
                            quotaService.finalizeDeduction(userId, estimated, BigDecimal.ZERO)
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .subscribe();

                            return Mono.just(ServerSentEvent.<String>builder()
                                    .data(buildChunk("Policy Violation: Request blocked by safety guardrail.", true))
                                    .build());
                        }

                        String decryptedCredentials = cryptoService.decrypt(model.getCredentialsEncrypted());

                        List<Map<String, Object>> rawHistory = request.getHistory() != null ? request.getHistory() : Collections.emptyList();
                        int maxHistoryMessages = model.getMaxHistoryMessages() * 2;
                        List<Map<String, Object>> slicedHistory = rawHistory.size() <= maxHistoryMessages
                                ? rawHistory
                                : rawHistory.subList(rawHistory.size() - maxHistoryMessages, rawHistory.size());

                        request.setHistory(slicedHistory);

                        AtomicReference<StringBuilder> responseAccumulator = new AtomicReference<>(new StringBuilder());
                        AtomicReference<Integer> providerPromptTokens = new AtomicReference<>(null);
                        AtomicReference<Integer> providerCompletionTokens = new AtomicReference<>(null);
                        AtomicBoolean finalized = new AtomicBoolean(false);
                        long startTime = System.currentTimeMillis();

                        Mono<List<ToolDto>> toolsMono = (model.isSupportsTools())
                                ? ((request.getTools() != null && !request.getTools().isEmpty())
                                        ? Mono.just(request.getTools())
                                        : mcpServerService.getActiveToolsForGroup(group.getId()).collectList())
                                : Mono.just(Collections.emptyList());

                        return toolsMono.flatMapMany(activeTools -> {
                            request.setTools(activeTools);
                            if (activeTools != null && !activeTools.isEmpty()) {
                                return executeAgenticToolLoop(request, model, decryptedCredentials);
                            }
                            return adapterFactory.resolve(model.getProvider())
                                    .streamChat(request, model, decryptedCredentials);
                        })
                                .timeout(Duration.ofMillis(model.getTimeoutMs()))
                                .onErrorResume(TimeoutException.class, ex -> {
                                    log.warn("[{}] Request timed out after {}ms", model.getProvider(), model.getTimeoutMs());
                                    return Flux.just("[Request timed out after " + model.getTimeoutMs() + "ms]");
                                })
                                .doOnNext(contentFragment -> {
                                    if (contentFragment != null && !contentFragment.isEmpty()) {
                                        if (contentFragment.contains("\"usage\"")) {
                                            try {
                                                JsonNode node = objectMapper.readTree(contentFragment);
                                                JsonNode usage = node.path("usage");
                                                if (!usage.isMissingNode() && !usage.isNull()) {
                                                    if (usage.has("prompt_tokens")) {
                                                        providerPromptTokens.set(usage.path("prompt_tokens").asInt());
                                                    }
                                                    if (usage.has("completion_tokens")) {
                                                        providerCompletionTokens.set(usage.path("completion_tokens").asInt());
                                                    }
                                                }
                                            } catch (Exception ignored) {}
                                        }

                                        if (contentFragment.startsWith("{") && contentFragment.contains("\"content\"")) {
                                            try {
                                                JsonNode node = objectMapper.readTree(contentFragment);
                                                String text = node.path("content").asText("");
                                                if (!text.isEmpty()) {
                                                    responseAccumulator.get().append(text);
                                                }
                                            } catch (Exception e) {
                                                responseAccumulator.get().append(contentFragment);
                                            }
                                        } else {
                                            responseAccumulator.get().append(contentFragment);
                                        }
                                    }
                                })
                                .map(contentFragment -> buildChunk(contentFragment, false))
                                .concatWith(Mono.just(buildChunk("", true)))
                                .doFinally(signalType -> {
                                    if (finalized.compareAndSet(false, true)) {
                                        long durationMs = System.currentTimeMillis() - startTime;
                                        meterRegistry.timer("ai.gateway.chat.latency", "provider", model.getProvider())
                                                .record(Duration.ofMillis(durationMs));
                                        meterRegistry.counter("ai.gateway.chat.requests", "provider", model.getProvider(), "status", signalType.name())
                                                .increment();

                                        String fullResponse = responseAccumulator.get().toString();

                                        // Provider-First Usage with Local Tokenizer Fallback
                                        int finalInputTokens = (providerPromptTokens.get() != null && providerPromptTokens.get() > 0)
                                                ? providerPromptTokens.get()
                                                : inputTokens;

                                        int finalOutputTokens = (providerCompletionTokens.get() != null && providerCompletionTokens.get() > 0)
                                                ? providerCompletionTokens.get()
                                                : tokenizerUtil.countTokens(model.getModelName(), fullResponse);

                                        BigDecimal actualCredits = BigDecimal.valueOf(finalInputTokens)
                                                .multiply(inMult)
                                                .add(BigDecimal.valueOf(finalOutputTokens).multiply(outMult))
                                                .setScale(4, RoundingMode.HALF_UP);

                                        String usageSource = (providerPromptTokens.get() != null || providerCompletionTokens.get() != null)
                                                ? "PROVIDER" : "LOCAL_FALLBACK";

                                        log.info("Chat finalized [{}] provider={}. in={} out={} (source={}) credits={}",
                                                signalType, model.getProvider(), finalInputTokens, finalOutputTokens, usageSource, actualCredits);

                                        eventPublisher.publishEvent(new TokenUsageRecordedEvent(group.getId(), model.getId(), finalInputTokens, finalOutputTokens));

                                        quotaService.finalizeDeduction(userId, estimated, actualCredits)
                                                .then(saveChatLog(userId, request, model, fullResponse))
                                                .subscribeOn(Schedulers.boundedElastic())
                                                .subscribe(null, err -> log.error("Post-stream finalization failed: {}", err.getMessage()));
                                    }
                                })
                                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());
                    });
                });
    }

    /**
     * Evaluates a prompt against a group's configured safety model guardrail.
     *
     * @param safetyModelId Safety model ID
     * @param userPrompt    Raw user prompt
     * @return Mono emitting true if prompt is safe, false if blocked
     */
    private Mono<Boolean> evaluateGuardrail(String safetyModelId, String userPrompt) {
        return modelRepository.findById(safetyModelId)
                .flatMap(safetyModel -> {
                    String credentials = cryptoService.decrypt(safetyModel.getCredentialsEncrypted());
                    ChatRequest guardReq = new ChatRequest();
                    guardReq.setModelId(safetyModelId);
                    guardReq.setMessage("Evaluate if the following prompt violates safety guidelines. Respond ONLY with SAFE or UNSAFE.\n\nPrompt: " + userPrompt);

                    return adapterFactory.resolve(safetyModel.getProvider())
                            .streamChat(guardReq, safetyModel, credentials)
                            .collectList()
                            .map(chunks -> String.join("", chunks))
                            .map(resp -> !resp.toUpperCase().contains("UNSAFE"))
                            .onErrorReturn(true);
                })
                .onErrorReturn(true);
    }

    /**
     * Retrieves paginated chat history for the authenticated user.
     *
     * @param userId Authenticated user ID
     * @param page   Page index (0-based)
     * @param size   Page size
     * @return Flux of ChatLog entities
     */
    public Flux<ChatLog> getChatHistory(String userId, int page, int size) {
        return chatLogRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    /**
     * Internal pipeline step to construct JSON SSE chunk payloads.
     *
     * @param content Text chunk content
     * @param done    Boolean flag indicating whether stream has completed
     * @return Serialized JSON chunk string
     */
    private String buildChunk(String content, boolean done) {
        return JsonUtil.toJsonString(Map.of("content", content != null ? content : "", "done", done));
    }

    /**
     * Asynchronously persists chat audit log records to the database.
     *
     * @param userId   Authenticated user ID
     * @param request  Original chat request
     * @param model    Model entity used for generation
     * @param response Complete synthesized response text
     * @return Mono emitting persisted ChatLog entity
     */
    private Mono<ChatLog> saveChatLog(String userId, ChatRequest request, Model model, String response) {
        String displayName = (model.getName() != null && !model.getName().isBlank())
                ? model.getName()
                : model.getModelName();

        ChatLog chatLog = ChatLog.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .modelId(model.getId())
                .modelDisplayName(displayName)
                .sessionId(request.getSessionId())
                .prompt(request.getMessage())
                .response(response)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        return entityTemplate.insert(chatLog);
    }

    /**
     * Fallback credit rate calculation if no custom rate is configured for the model.
     *
     * @param modelId Model ID
     * @return Default CreditRate entity
     */
    private CreditRate defaultRate(String modelId) {
        return CreditRate.builder()
                .modelId(modelId)
                .inputMultiplier(BigDecimal.ONE)
                .outputMultiplier(BigDecimal.valueOf(2))
                .build();
    }

    /**
     * Multi-step Agentic Tool Execution Loop:
     * <ol>
     *   <li>Pass 1: Invokes LLM provider with available tool definitions.</li>
     *   <li>Parse Tool Calls: Identifies requested tool invocations from LLM stream.</li>
     *   <li>Execute Tools: Invokes MCP tools via McpServerService.</li>
     *   <li>Pass 2: Re-invokes LLM provider with tool execution results to synthesize answer.</li>
     * </ol>
     *
     * @param request              Original chat request
     * @param model                Target AI Model entity
     * @param decryptedCredentials Decrypted credentials for AI provider
     * @return Flux of streamed response text chunks
     */
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
                        return Mono.empty();
                    }
                    return Mono.just(fragment);
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
                                @SuppressWarnings("unchecked")
                                Map<String, Object> func = (Map<String, Object>) tc.get("function");
                                String name = (String) func.get("name");
                                Object rawArgs = func.get("arguments");
                                Map<String, Object> argsMap = Collections.emptyMap();
                                if (rawArgs instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> castArgs = (Map<String, Object>) rawArgs;
                                    argsMap = castArgs;
                                } else if (rawArgs instanceof String && !((String) rawArgs).isBlank()) {
                                    argsMap = JsonUtil.parseJsonMap((String) rawArgs);
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

    private List<Map<String, Object>> parseToolCallsFromStream(String rawStreamText) {
        if (rawStreamText == null || rawStreamText.isBlank()) return Collections.emptyList();
        Map<Integer, Map<String, Object>> toolMap = new LinkedHashMap<>();
        String trimmedText = rawStreamText.trim();

        try {
            if (trimmedText.startsWith("{") && trimmedText.contains("\"tool_calls\"")) {
                try {
                    Map<String, Object> directObj = JsonUtil.parseJsonMap(trimmedText);
                    if (directObj.containsKey("tool_calls") && directObj.get("tool_calls") instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> tcList = (List<Map<String, Object>>) directObj.get("tool_calls");
                        for (Map<String, Object> tc : tcList) {
                            if (tc != null && tc.containsKey("function")) {
                                String callId = tc.get("id") != null ? tc.get("id").toString() : "call_1";
                                @SuppressWarnings("unchecked")
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

            Matcher m = Pattern.compile("(\\{.*?\"tool_calls\":\\[.*?\\]\\})").matcher(rawStreamText);
            while (m.find()) {
                String jsonStr = m.group(1);
                try {
                    Map<String, Object> chunk = JsonUtil.parseJsonMap(jsonStr);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tcList = (List<Map<String, Object>>) chunk.get("tool_calls");
                    if (tcList != null) {
                        for (Map<String, Object> tc : tcList) {
                            int idx = tc.containsKey("index") ? ((Number) tc.get("index")).intValue() : 0;
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

                            if (tc.get("id") != null) tool.put("id", tc.get("id"));
                            @SuppressWarnings("unchecked")
                            Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                            if (fn != null) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> targetFn = (Map<String, Object>) tool.get("function");
                                if (fn.get("name") != null && !fn.get("name").toString().isBlank()) {
                                    targetFn.put("name", fn.get("name"));
                                }
                                if (fn.get("arguments") != null) {
                                    String currentArgs = (String) targetFn.get("arguments");
                                    targetFn.put("arguments", currentArgs + fn.get("arguments").toString());
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

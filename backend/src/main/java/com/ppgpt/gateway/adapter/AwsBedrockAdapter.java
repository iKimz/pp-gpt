package com.ppgpt.gateway.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppgpt.gateway.domain.Model;
import com.ppgpt.gateway.dto.ChatRequest;
import com.ppgpt.gateway.dto.ToolDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.PayloadPart;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AwsBedrockAdapter implements AiProviderAdapter {

    private final ObjectMapper objectMapper;

    @Override
    public String providerKey() {
        return "AWS_BEDROCK";
    }

    @Override
    public Flux<String> streamChat(ChatRequest request, Model model, String decryptedCredentials) {
        JsonNode creds = parseCreds(decryptedCredentials);
        String apiKey = requireField(creds, "apiKey");
        String region = creds.path("region").asText("us-east-1");

        byte[] bodyBytes;
        try {
            Map<String, Object> bodyMap = buildRequestBody(request, model);
            bodyBytes = objectMapper.writeValueAsBytes(bodyMap);
        } catch (Exception e) {
            return Flux.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize Bedrock request: " + e.getMessage()));
        }

        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .putHeader("Authorization", "Bearer " + apiKey)
                .build();

        BedrockRuntimeAsyncClientBuilder clientBuilder = BedrockRuntimeAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .overrideConfiguration(overrideConfig);

        if (StringUtils.hasText(model.getEndpointUrl())) {
            clientBuilder.endpointOverride(URI.create(model.getEndpointUrl()));
        }

        BedrockRuntimeAsyncClient asyncClient = clientBuilder.build();

        InvokeModelWithResponseStreamRequest invokeReq = InvokeModelWithResponseStreamRequest.builder()
                .modelId(model.getModelName())
                .body(SdkBytes.fromByteArray(bodyBytes))
                .contentType("application/json")
                .build();

        Flux<String> responseFlux = Flux.create(sink -> {
            InvokeModelWithResponseStreamResponseHandler handler = InvokeModelWithResponseStreamResponseHandler
                    .builder()
                    .onEventStream(publisher -> publisher.subscribe(new org.reactivestreams.Subscriber<>() {

                        @Override
                        public void onSubscribe(org.reactivestreams.Subscription s) {
                            s.request(Long.MAX_VALUE);
                        }

                        @Override
                        public void onNext(software.amazon.awssdk.services.bedrockruntime.model.ResponseStream event) {
                            if (event instanceof PayloadPart pp) {
                                String text = extractBedrockContent(pp.bytes().asUtf8String());
                                if (text != null) {
                                    sink.next(text);
                                }
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            sink.error(t);
                        }

                        @Override
                        public void onComplete() {
                            sink.complete();
                        }
                    }))
                    .onError(sink::error)
                    .build();

            asyncClient.invokeModelWithResponseStream(invokeReq, handler)
                    .whenComplete((res, err) -> {
                        if (err != null) {
                            sink.error(err);
                        }
                        asyncClient.close();
                    });

            sink.onCancel(asyncClient::close);
            sink.onDispose(asyncClient::close);
        });

        // Apply timeout
        if (model.getTimeoutMs() > 0) {
            responseFlux = responseFlux.timeout(Duration.ofMillis(model.getTimeoutMs()))
                    .onErrorResume(TimeoutException.class, e -> Flux.error(
                            new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "[Bedrock] Request timed out")));
        }

        return responseFlux;
    }

    private Map<String, Object> buildRequestBody(ChatRequest request, Model model) {
        String modelIdName = (model.getModelName() != null ? model.getModelName() : "").toLowerCase();
        boolean isAnthropic = modelIdName.contains("anthropic") || modelIdName.contains("claude");

        StringBuilder systemContent = new StringBuilder();
        if (model.getSystemPrompt() != null && !model.getSystemPrompt().isBlank()) {
            systemContent.append(model.getSystemPrompt());
        }
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            if (!systemContent.isEmpty()) systemContent.append("\n\n");
            systemContent.append("IMPORTANT INSTRUCTION: You have access to real-time external tools in the 'tools' list (including get_current_time for real-time location time queries, convert_unit, etc.). Whenever the user asks for real-time information, current time, or calculations, YOU MUST USE THE PROVIDED TOOLS instead of refusing or saying you do not have tool access.");
        }

        List<Map<String, Object>> bedrockMessages = new ArrayList<>();

        // System prompt for non-Anthropic models goes into messages
        if (!isAnthropic && !systemContent.isEmpty()) {
            bedrockMessages.add(Map.of(
                    "role", "system",
                    "content", systemContent.toString()));
        }

        // Process message history preserving tool fields
        if (request.getHistory() != null) {
            for (Map<String, Object> turn : request.getHistory()) {
                String role = turn.get("role") != null ? turn.get("role").toString() : "user";
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("role", role);

                if (turn.containsKey("content")) {
                    msg.put("content", turn.get("content"));
                }
                if (turn.containsKey("tool_call_id")) {
                    msg.put("tool_call_id", turn.get("tool_call_id"));
                }
                if (turn.containsKey("name")) {
                    msg.put("name", turn.get("name"));
                }
                if (turn.containsKey("tool_calls")) {
                    msg.put("tool_calls", turn.get("tool_calls"));
                }
                bedrockMessages.add(msg);
            }
        }

        // Add user message / images
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            List<Map<String, Object>> contentBlocks = new ArrayList<>();
            if (request.getMessage() != null && !request.getMessage().isBlank()) {
                contentBlocks.add(Map.of("type", "text", "text", request.getMessage()));
            }
            for (String imgDataUrl : request.getImages()) {
                if (isAnthropic) {
                    String mediaType = "image/png";
                    String base64Data = imgDataUrl;
                    if (imgDataUrl.contains(";base64,")) {
                        String[] parts = imgDataUrl.split(";base64,", 2);
                        if (parts[0].startsWith("data:")) {
                            mediaType = parts[0].substring(5);
                        }
                        base64Data = parts[1];
                    }
                    contentBlocks.add(Map.of(
                            "type", "image",
                            "source", Map.of(
                                    "type", "base64",
                                    "media_type", mediaType,
                                    "data", base64Data
                            )
                    ));
                } else {
                    contentBlocks.add(Map.of("type", "image_url", "image_url", Map.of("url", imgDataUrl)));
                }
            }
            bedrockMessages.add(Map.of(
                    "role", "user",
                    "content", contentBlocks));
        } else {
            bedrockMessages.add(Map.of(
                    "role", "user",
                    "content", request.getMessage()));
        }

        Map<String, Object> bodyMap = new LinkedHashMap<>();

        if (isAnthropic) {
            bodyMap.put("anthropic_version", "bedrock-2023-05-31");
            if (!systemContent.isEmpty()) {
                bodyMap.put("system", systemContent.toString());
            }
        } else {
            bodyMap.put("model", model.getModelName());
            bodyMap.put("stream", true);
        }

        bodyMap.put("messages", bedrockMessages);
        bodyMap.put("max_tokens", 4096);
        bodyMap.put("temperature", model.getTemperature());

        // Convert and attach Tools if present
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            List<Map<String, Object>> toolsList = new ArrayList<>();

            for (ToolDto tool : request.getTools()) {
                if (tool == null) continue;
                String toolName = "";
                String toolDesc = "";
                Object inputSchema = Collections.emptyMap();

                if (tool.function() != null) {
                    toolName = tool.function().name() != null ? tool.function().name() : "";
                    toolDesc = tool.function().description() != null ? tool.function().description() : "";
                    if (tool.function().parameters() != null) {
                        inputSchema = tool.function().parameters();
                    }
                }

                if (isAnthropic) {
                    toolsList.add(Map.of(
                            "name", toolName,
                            "description", toolDesc,
                            "input_schema", inputSchema
                    ));
                } else {
                    toolsList.add(Map.of(
                            "type", "function",
                            "function", Map.of(
                                    "name", toolName,
                                    "description", toolDesc,
                                    "parameters", inputSchema
                            )
                    ));
                }
            }

            bodyMap.put("tools", toolsList);
        }

        try {
            log.debug("[Bedrock] Built request payload: {}", objectMapper.writeValueAsString(bodyMap));
        } catch (Exception e) {
            log.warn("[Bedrock] Error serializing debug payload", e);
        }

        return bodyMap;
    }

    private String extractBedrockContent(String raw) {
        try {
            if (raw == null || raw.isBlank() || !raw.trim().startsWith("{"))
                return null;
            JsonNode node = objectMapper.readTree(raw);

            // 1. OpenAI format tool_calls (returned by OpenAI-style Bedrock proxies / OSS models)
            JsonNode choices = node.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).path("delta");
                if (delta.has("tool_calls")) {
                    return objectMapper.writeValueAsString(Map.of("tool_calls", delta.path("tool_calls")));
                }
                JsonNode deltaContent = delta.path("content");
                if (!deltaContent.isMissingNode() && !deltaContent.isNull()) {
                    return stripReasoning(deltaContent.asText());
                }
            }

            // 2. Claude 3 / Bedrock Native Tool Calling Events
            // A) Tool Call Start Event
            if ("content_block_start".equals(node.path("type").asText())) {
                JsonNode cb = node.path("content_block");
                if ("tool_use".equals(cb.path("type").asText())) {
                    String toolId = cb.path("id").asText("call_1");
                    String toolName = cb.path("name").asText();
                    Map<String, Object> toolCallObj = Map.of(
                            "tool_calls", List.of(Map.of(
                                    "index", 0,
                                    "id", toolId,
                                    "type", "function",
                                    "function", Map.of("name", toolName, "arguments", "")
                            ))
                    );
                    return objectMapper.writeValueAsString(toolCallObj);
                }
            }

            // B) Tool Call Input Delta (Arguments Chunk)
            if ("content_block_delta".equals(node.path("type").asText())) {
                JsonNode deltaNode = node.path("delta");
                if ("input_json_delta".equals(deltaNode.path("type").asText())) {
                    String partialJson = deltaNode.path("partial_json").asText("");
                    Map<String, Object> toolArgObj = Map.of(
                            "tool_calls", List.of(Map.of(
                                    "index", 0,
                                    "function", Map.of("arguments", partialJson)
                            ))
                    );
                    return objectMapper.writeValueAsString(toolArgObj);
                }
                if ("text_delta".equals(deltaNode.path("type").asText())) {
                    return stripReasoning(deltaNode.path("text").asText(""));
                }
            }

            // 3. Native Bedrock contentBlockDelta
            JsonNode textDelta = node.path("contentBlockDelta").path("delta").path("text");
            if (!textDelta.isMissingNode() && !textDelta.isNull()) {
                return stripReasoning(textDelta.asText());
            }

            // 4. Claude 2 completion format
            JsonNode completion = node.path("completion");
            if (!completion.isMissingNode() && !completion.isNull()) {
                return stripReasoning(completion.asText());
            }

            // 5. Generic delta.text
            JsonNode deltaText = node.path("delta").path("text");
            if (!deltaText.isMissingNode() && !deltaText.isNull()) {
                return stripReasoning(deltaText.asText());
            }

        } catch (JsonProcessingException e) {
            log.trace("[Bedrock] Non-JSON chunk, skipping");
        }
        return null;
    }

    private String stripReasoning(String text) {
        if (text == null) return "";
        if (text.contains("<reasoning>")) {
            text = text.replaceAll("(?s)<reasoning>.*?</reasoning>", "");
        }
        if (text.contains("<think>")) {
            text = text.replaceAll("(?s)<think>.*?</think>", "");
        }
        return text;
    }

    private JsonNode parseCreds(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Invalid credentials JSON for Bedrock model: " + e.getMessage());
        }
    }

    private String requireField(JsonNode node, String field) {
        JsonNode val = node.path(field);
        if (val.isMissingNode() || val.isNull() || val.asText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Missing '" + field + "' in Bedrock credentials");
        }
        return val.asText();
    }
}


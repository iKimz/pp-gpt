package com.ppgpt.gateway.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppgpt.gateway.domain.Model;
import com.ppgpt.gateway.dto.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for standard OpenAI-compatible providers.
 *
 * <p>
 * Credentials JSON: {@code {"apiKey": "sk-..."}}
 *
 * <p>
 * Injects {@code Authorization: Bearer <apiKey>} and proxies to
 * the model's {@code endpoint_url} verbatim.
 *
 * <p>
 * Message order (per OpenAI spec):
 * <ol>
 * <li>{@code system} message (if systemPrompt is configured)</li>
 * <li>Previous conversation turns (history, sliced by ChatService)</li>
 * <li>New {@code user} message</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiAdapter implements AiProviderAdapter {

    private final WebClient aiWebClient;
    private final ObjectMapper objectMapper;

    @Override
    public String providerKey() {
        return "OPENAI";
    }

    @Override
    public Flux<String> streamChat(ChatRequest request, Model model, String decryptedCredentials) {
        String apiKey = extractField(decryptedCredentials, "apiKey");

        List<Map<String, Object>> messages = buildMessages(request, model);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.getModelName());
        body.put("messages", messages);
        body.put("temperature", model.getTemperature());
        body.put("stream", true);
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", request.getTools());
        }

        try {
            log.debug("[OpenAI] Built request payload: {}", objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            log.warn("[OpenAI] Could not serialize debug payload", e);
        }

        return aiWebClient.post()
                .uri(model.getEndpointUrl())
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(
                        s -> s.is4xxClientError() || s.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty("No error body provided")
                                .map(err -> new ResponseStatusException(resp.statusCode(),
                                        "[OpenAI] provider error: " + err)))
                .bodyToFlux(String.class)
                .takeWhile(line -> !line.contains("[DONE]"))
                .mapNotNull(line -> extractContent(line.trim()));
    }

    // ─── Message Builder ─────────────────────────────────────────────────────

    static List<Map<String, Object>> buildMessages(ChatRequest request, Model model) {
        List<Map<String, Object>> messages = new ArrayList<>();
        // 1. System prompt (if configured)
        if (model.getSystemPrompt() != null && !model.getSystemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", model.getSystemPrompt()));
        }
        // 2. History (already sliced by ChatService)
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
                messages.add(msg);
            }
        }
        // 3. New user message (text or multimodal array if images attached)
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            List<Map<String, Object>> contentParts = new ArrayList<>();
            if (request.getMessage() != null && !request.getMessage().isBlank()) {
                contentParts.add(Map.of("type", "text", "text", request.getMessage()));
            }
            for (String imgUrl : request.getImages()) {
                contentParts.add(Map.of("type", "image_url", "image_url", Map.of("url", imgUrl)));
            }
            messages.add(Map.of("role", "user", "content", contentParts));
        } else if (request.getMessage() != null && !request.getMessage().isBlank()) {
            messages.add(Map.of("role", "user", "content", request.getMessage()));
        }
        return messages;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String extractField(String json, String field) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode val = node.path(field);
            if (val.isMissingNode() || val.isNull()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Missing '" + field + "' in credentials for OpenAI model");
            }
            return val.asText();
        } catch (JsonProcessingException e) {
            // Legacy plain-text key (backwards compat)
            log.warn("Credentials are not JSON, treating as plain apiKey");
            return json.trim();
        }
    }

    private String extractContent(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode choice = node.path("choices").path(0);
            JsonNode delta = choice.path("delta");

            JsonNode toolCalls = delta.path("tool_calls");
            if (toolCalls.isMissingNode() || toolCalls.isNull()) {
                toolCalls = choice.path("message").path("tool_calls");
            }
            if (!toolCalls.isMissingNode() && !toolCalls.isNull() && toolCalls.isArray() && toolCalls.size() > 0) {
                Map<String, Object> chunk = new LinkedHashMap<>();
                chunk.put("content", "");
                chunk.put("tool_calls", objectMapper.treeToValue(toolCalls, Object.class));
                return objectMapper.writeValueAsString(chunk);
            }

            JsonNode content = delta.path("content");
            if (!content.isMissingNode() && !content.isNull())
                return content.asText();
            JsonNode msg = choice.path("message").path("content");
            if (!msg.isMissingNode() && !msg.isNull())
                return msg.asText();
        } catch (Exception e) {
            log.trace("Non-JSON SSE chunk: {}", json);
        }
        return null;
    }
}

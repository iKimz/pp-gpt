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
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for Azure OpenAI Service.
 *
 * <p>
 * Credentials JSON:
 * 
 * <pre>{@code {"apiKey": "...", "apiVersion": "2024-02-01"}}</pre>
 *
 * <p>
 * Azure-specific differences from standard OpenAI:
 * <ul>
 * <li>Auth header: {@code api-key: <value>} (not Bearer)</li>
 * <li>Query param: {@code ?api-version=<value>} appended to endpoint URL</li>
 * <li>Request body does NOT include a {@code model} field (model is in the URL
 * path)</li>
 * </ul>
 *
 * <p>
 * Endpoint URL example:
 * {@code https://<resource>.openai.azure.com/openai/deployments/<deployment>/chat/completions}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureOpenAiAdapter implements AiProviderAdapter {

    private static final String DEFAULT_API_VERSION = "2024-02-01";

    private final WebClient aiWebClient;
    private final ObjectMapper objectMapper;

    @Override
    public String providerKey() {
        return "AZURE";
    }

    @Override
    public Flux<String> streamChat(ChatRequest request, Model model, String decryptedCredentials) {
        JsonNode creds = parseCreds(decryptedCredentials);
        String apiKey = requireField(creds, "apiKey");
        String apiVersion = creds.path("apiVersion").isMissingNode()
                ? DEFAULT_API_VERSION
                : creds.path("apiVersion").asText(DEFAULT_API_VERSION);

        String baseEndpoint = model.getEndpointUrl();
        if (!baseEndpoint.contains("/chat/completions")) {
            baseEndpoint = baseEndpoint.replaceAll("/+$", "")
                    + "/openai/deployments/" + model.getModelName() + "/chat/completions";
        }

        // Append api-version query parameter to the endpoint URL
        String uri = UriComponentsBuilder.fromUriString(baseEndpoint)
                .queryParam("api-version", apiVersion)
                .build()
                .toUriString();

        // Azure body: no "model" field; deployment is embedded in the URL
        // System prompt + history + new user message (same spec as OpenAI)
        List<Map<String, String>> messages = OpenAiAdapter.buildMessages(request, model);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", messages);
        body.put("temperature", model.getTemperature());
        body.put("stream", true);

        try {
            log.debug("[Azure] Built request payload: {}", objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            log.warn("[Azure] Could not serialize debug payload", e);
        }

        return aiWebClient.post()
                .uri(uri)
                .header("api-key", apiKey) // Azure auth header
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(
                        s -> s.is4xxClientError() || s.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty("No error body provided")
                                .map(err -> new ResponseStatusException(resp.statusCode(),
                                        "[Azure] provider error: " + err)))
                .bodyToFlux(String.class)
                .takeWhile(line -> !line.contains("[DONE]"))
                .mapNotNull(line -> extractContent(line.trim()));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private JsonNode parseCreds(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Invalid credentials JSON for Azure model: " + e.getMessage());
        }
    }

    private String requireField(JsonNode node, String field) {
        JsonNode val = node.path(field);
        if (val.isMissingNode() || val.isNull() || val.asText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Missing '" + field + "' in Azure credentials");
        }
        return val.asText();
    }

    private String extractContent(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode delta = node.path("choices").path(0).path("delta").path("content");
            if (!delta.isMissingNode() && !delta.isNull())
                return delta.asText();
            JsonNode msg = node.path("choices").path(0).path("message").path("content");
            if (!msg.isMissingNode() && !msg.isNull())
                return msg.asText();
        } catch (JsonProcessingException e) {
            log.trace("[Azure] Non-JSON SSE chunk: {}", json);
        }
        return null;
    }
}

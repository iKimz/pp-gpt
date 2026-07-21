package com.ppgpt.gateway.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppgpt.gateway.domain.Model;
import com.ppgpt.gateway.dto.ChatRequest;
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
        List<Map<String, Object>> bedrockMessages = new ArrayList<>();

        if (request.getHistory() != null) {
            for (Map<String, String> turn : request.getHistory()) {
                String role = turn.get("role");
                if (role == null || role.isBlank())
                    role = "user";

                String content = turn.get("content");
                if (content == null)
                    content = "";

                bedrockMessages.add(Map.of(
                        "role", role,
                        "content", content));
            }
        }

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            List<Map<String, Object>> contentBlocks = new ArrayList<>();
            if (request.getMessage() != null && !request.getMessage().isBlank()) {
                contentBlocks.add(Map.of("type", "text", "text", request.getMessage()));
            }
            for (String imgDataUrl : request.getImages()) {
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

        bodyMap.put("anthropic_version", "bedrock-2023-05-31");

        if (model.getSystemPrompt() != null && !model.getSystemPrompt().isBlank()) {
            bodyMap.put("system", model.getSystemPrompt());
        }

        bodyMap.put("messages", bedrockMessages);
        bodyMap.put("max_tokens", 4096);
        bodyMap.put("temperature", model.getTemperature());

        try {
            log.debug("[Bedrock] Built request payload: {}", objectMapper.writeValueAsString(bodyMap));
        } catch (Exception e) {
        }

        return bodyMap;
    }

    private String extractBedrockContent(String raw) {
        try {
            if (raw == null || raw.isBlank() || !raw.trim().startsWith("{"))
                return null;
            JsonNode node = objectMapper.readTree(raw);

            // 1. OpenAI format (returned by some Bedrock proxies like LiteLLM)
            JsonNode choices = node.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode deltaContent = choices.get(0).path("delta").path("content");
                if (!deltaContent.isMissingNode() && !deltaContent.isNull())
                    return deltaContent.asText();
            }

            // 2. Claude 3 format (Bedrock native)
            JsonNode text = node.path("contentBlockDelta").path("delta").path("text");
            if (!text.isMissingNode() && !text.isNull())
                return text.asText();

            // 3. Claude 2 format (completion)
            JsonNode completion = node.path("completion");
            if (!completion.isMissingNode() && !completion.isNull())
                return completion.asText();

            // 4. Claude generic delta
            JsonNode deltaText = node.path("delta").path("text");
            if (!deltaText.isMissingNode() && !deltaText.isNull())
                return deltaText.asText();

        } catch (JsonProcessingException e) {
            log.trace("[Bedrock] Non-JSON chunk, skipping");
        }
        return null;
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

package com.ppgpt.gateway.service;

import com.ppgpt.gateway.domain.McpServer;
import com.ppgpt.gateway.domain.McpTool;
import com.ppgpt.gateway.domain.McpResource;
import com.ppgpt.gateway.domain.McpPrompt;
import com.ppgpt.gateway.domain.GroupMcpToolAccess;
import com.ppgpt.gateway.dto.CreateMcpServerRequest;
import com.ppgpt.gateway.dto.McpServerDto;
import com.ppgpt.gateway.dto.McpToolDto;
import com.ppgpt.gateway.dto.GroupToolAccessRequest;
import com.ppgpt.gateway.dto.CreateManualToolRequest;
import com.ppgpt.gateway.dto.OpenApiImportRequest;
import com.ppgpt.gateway.repository.McpServerRepository;
import com.ppgpt.gateway.repository.McpToolRepository;
import com.ppgpt.gateway.repository.McpResourceRepository;
import com.ppgpt.gateway.repository.McpPromptRepository;
import com.ppgpt.gateway.repository.GroupMcpToolAccessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerService {

    private static final Pattern AUTH_URI_PATTERN = Pattern.compile("authorization_uri=[\"']([^\"']+)[\"']");
    private static final Pattern RESOURCE_META_PATTERN = Pattern.compile("resource_metadata=[\"']([^\"']+)[\"']");

    private final McpServerRepository mcpServerRepository;
    private final McpToolRepository mcpToolRepository;
    private final McpResourceRepository mcpResourceRepository;
    private final McpPromptRepository mcpPromptRepository;
    private final GroupMcpToolAccessRepository groupMcpToolAccessRepository;
    private final CryptoService cryptoService;
    private final WebClient aiWebClient;
    private final ObjectMapper objectMapper;

    public static class OAuthDiscoveryResult {
        public String authorizeUrl;
        public String tokenUrl;
        public String registrationUrl;

        public OAuthDiscoveryResult(String authorizeUrl, String tokenUrl, String registrationUrl) {
            this.authorizeUrl = authorizeUrl;
            this.tokenUrl = tokenUrl;
            this.registrationUrl = registrationUrl;
        }
    }

    @SuppressWarnings("unchecked")
    private Mono<OAuthDiscoveryResult> discoverOAuthMetadata(String wwwAuthHeader) {
        if (wwwAuthHeader == null || wwwAuthHeader.isBlank()) return Mono.empty();

        // 1. Direct authorization_uri="https://..."
        Matcher authMatcher = AUTH_URI_PATTERN.matcher(wwwAuthHeader);
        if (authMatcher.find()) {
            return Mono.just(new OAuthDiscoveryResult(authMatcher.group(1), null, null));
        }

        // 2. RFC 9207 / RFC 8414 Well-Known Resource Metadata Discovery
        Matcher metaMatcher = RESOURCE_META_PATTERN.matcher(wwwAuthHeader);
        if (metaMatcher.find()) {
            String metaUrl = metaMatcher.group(1);
            return aiWebClient.get()
                    .uri(metaUrl)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .flatMap(meta -> {
                        List<String> authServers = (List<String>) meta.get("authorization_servers");
                        if (authServers != null && !authServers.isEmpty()) {
                            String authServerBase = authServers.get(0).replaceAll("/+$", "");
                            String discoveryUrl = authServerBase + "/.well-known/oauth-authorization-server";
                            return aiWebClient.get()
                                    .uri(discoveryUrl)
                                    .retrieve()
                                    .bodyToMono(Map.class)
                                    .map(disc -> new OAuthDiscoveryResult(
                                            (String) disc.get("authorization_endpoint"),
                                            (String) disc.get("token_endpoint"),
                                            (String) disc.get("registration_endpoint")
                                    ));
                        }
                        return Mono.empty();
                    })
                    .onErrorResume(e -> {
                        log.warn("[MCP OAuth] Metadata discovery failed for header {}: {}", wwwAuthHeader, e.getMessage());
                        return Mono.empty();
                    });
        }

        return Mono.empty();
    }

    @SuppressWarnings("unchecked")
    private Mono<String> registerDynamicClient(String registrationUrl, String redirectUri) {
        if (registrationUrl == null || registrationUrl.isBlank()) return Mono.empty();

        Map<String, Object> regBody = Map.of(
                "client_name", "pp-gpt Gateway",
                "redirect_uris", List.of(redirectUri),
                "grant_types", List.of("authorization_code", "refresh_token"),
                "response_types", List.of("code")
        );

        return aiWebClient.post()
                .uri(registrationUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(regBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(resp -> (String) resp.get("client_id"))
                .onErrorResume(e -> {
                    log.warn("[MCP OAuth] Dynamic client registration at {} failed: {}", registrationUrl, e.getMessage());
                    return Mono.empty();
                });
    }

    public Flux<McpServerDto> getAllMcpServers() {
        return mcpServerRepository.findAll()
                .map(this::toDto);
    }

    public Mono<McpServerDto> createMcpServer(CreateMcpServerRequest request) {
        String id = UUID.randomUUID().toString();
        String encryptedKey = (request.getApiKey() != null && !request.getApiKey().isBlank())
                ? cryptoService.encrypt(request.getApiKey().trim())
                : null;

        String encryptedClientSecret = (request.getOauthClientSecret() != null && !request.getOauthClientSecret().isBlank())
                ? cryptoService.encrypt(request.getOauthClientSecret().trim())
                : null;

        McpServer server = McpServer.builder()
                .id(id)
                .name(request.getName().trim())
                .endpointUrl(request.getEndpointUrl().trim())
                .authType(request.getAuthType() != null ? request.getAuthType() : "STATIC_KEY")
                .apiKeyEncrypted(encryptedKey)
                .oauthAuthorizeUrl(request.getOauthAuthorizeUrl())
                .oauthTokenUrl(request.getOauthTokenUrl())
                .oauthClientId(request.getOauthClientId())
                .oauthClientSecretEncrypted(encryptedClientSecret)
                .description(request.getDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .supportsTools(true)
                .supportsResources(false)
                .supportsPrompts(false)
                .capabilityStatus("DISCOVERED")
                .createdAt(LocalDateTime.now())
                .newEntity(true)
                .build();

        return mcpServerRepository.save(server)
                .map(this::toDto);
    }

    public Mono<McpServerDto> updateMcpServer(String id, CreateMcpServerRequest request) {
        return mcpServerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP Server not found")))
                .flatMap(existing -> {
                    existing.setName(request.getName().trim());
                    existing.setEndpointUrl(request.getEndpointUrl().trim());
                    if (request.getAuthType() != null) {
                        existing.setAuthType(request.getAuthType());
                    }
                    existing.setDescription(request.getDescription());
                    if (request.getIsActive() != null) {
                        existing.setIsActive(request.getIsActive());
                    }
                    if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
                        existing.setApiKeyEncrypted(cryptoService.encrypt(request.getApiKey().trim()));
                    }
                    if (request.getOauthAuthorizeUrl() != null) {
                        existing.setOauthAuthorizeUrl(request.getOauthAuthorizeUrl());
                    }
                    if (request.getOauthTokenUrl() != null) {
                        existing.setOauthTokenUrl(request.getOauthTokenUrl());
                    }
                    if (request.getOauthClientId() != null) {
                        existing.setOauthClientId(request.getOauthClientId());
                    }
                    if (request.getOauthClientSecret() != null && !request.getOauthClientSecret().isBlank()) {
                        existing.setOauthClientSecretEncrypted(cryptoService.encrypt(request.getOauthClientSecret().trim()));
                    }
                    existing.setNewEntity(false);
                    return mcpServerRepository.save(existing);
                })
                .map(this::toDto);
    }

    public Mono<Void> deleteMcpServer(String id) {
        return mcpServerRepository.deleteById(id);
    }

    public Mono<Map<String, Object>> testConnection(String id) {
        return mcpServerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP Server not found")))
                .flatMap(server -> {
                    WebClient.RequestBodySpec spec = aiWebClient.post()
                            .uri(server.getEndpointUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Accept", "application/json, text/event-stream");

                    if ("STATIC_KEY".equals(server.getAuthType()) && server.getApiKeyEncrypted() != null && !server.getApiKeyEncrypted().isBlank()) {
                        String rawKey = cryptoService.decrypt(server.getApiKeyEncrypted());
                        spec.header("Authorization", "Bearer " + rawKey);
                    } else if ("OAUTH2".equals(server.getAuthType()) && server.getOauthAccessTokenEncrypted() != null && !server.getOauthAccessTokenEncrypted().isBlank()) {
                        String rawToken = cryptoService.decrypt(server.getOauthAccessTokenEncrypted());
                        spec.header("Authorization", "Bearer " + rawToken);
                    }

                    Map<String, Object> jsonRpcBody = Map.of(
                            "jsonrpc", "2.0",
                            "method", "tools/list",
                            "id", 1
                    );

                    return spec.bodyValue(jsonRpcBody)
                            .exchangeToMono(response -> {
                                HttpStatus status = HttpStatus.valueOf(response.statusCode().value());
                                if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                                    String wwwAuth = response.headers().header("WWW-Authenticate").stream().findFirst().orElse(null);

                                    return discoverOAuthMetadata(wwwAuth)
                                            .flatMap(disc -> {
                                                String authUrl = disc.authorizeUrl;
                                                String tokenUrl = disc.tokenUrl;
                                                String regUrl = disc.registrationUrl;

                                                // Update server entity with discovered OAuth metadata
                                                server.setAuthType("OAUTH2");
                                                if (authUrl != null) server.setOauthAuthorizeUrl(authUrl);
                                                if (tokenUrl != null) server.setOauthTokenUrl(tokenUrl);

                                                // Check if dynamic client registration is required
                                                if ((server.getOauthClientId() == null || server.getOauthClientId().isBlank()) && regUrl != null) {
                                                    String redirectUri = "http://localhost/api/v1/mcp/oauth/callback";
                                                    return registerDynamicClient(regUrl, redirectUri)
                                                            .flatMap(clientId -> {
                                                                if (clientId != null && !clientId.isBlank()) {
                                                                    server.setOauthClientId(clientId);
                                                                }
                                                                server.setNewEntity(false);
                                                                return mcpServerRepository.save(server)
                                                                        .map(saved -> createUnauthorizedResponse(status.value(), authUrl, saved.getOauthClientId()));
                                                            })
                                                            .defaultIfEmpty(createUnauthorizedResponse(status.value(), authUrl, server.getOauthClientId()));
                                                }

                                                server.setNewEntity(false);
                                                return mcpServerRepository.save(server)
                                                        .map(saved -> createUnauthorizedResponse(status.value(), authUrl, saved.getOauthClientId()));
                                            })
                                            .defaultIfEmpty(createUnauthorizedResponse(status.value(), server.getOauthAuthorizeUrl(), server.getOauthClientId()));
                                }

                                return response.bodyToFlux(String.class)
                                        .collectList()
                                        .map(lines -> String.join("\n", lines))
                                        .map(rawBody -> {
                                            Map<String, Object> bodyMap = parseJsonResponse(rawBody);
                                            return Map.<String, Object>of(
                                                    "status", "CONNECTED",
                                                    "response", bodyMap.isEmpty() ? rawBody : bodyMap
                                            );
                                        });
                            })
                            .timeout(Duration.ofSeconds(5))
                            .onErrorResume(err -> Mono.just(Map.of(
                                    "status", "DISCONNECTED",
                                    "error", err.getMessage() != null ? err.getMessage() : "Connection timed out or failed"
                            )));
                });
    }

    private Map<String, Object> createUnauthorizedResponse(int httpStatus, String authorizeUrl, String clientId) {
        Map<String, Object> resMap = new LinkedHashMap<>();
        resMap.put("status", "UNAUTHORIZED");
        resMap.put("httpStatus", httpStatus);
        resMap.put("requiresOAuth", true);
        if (authorizeUrl != null && !authorizeUrl.isBlank()) {
            resMap.put("discoveredAuthorizeUrl", authorizeUrl);
        }
        if (clientId != null && !clientId.isBlank()) {
            resMap.put("oauthClientId", clientId);
        }
        resMap.put("message", "MCP Server requires authentication. Click Popup Login to authorize.");
        return resMap;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonResponse(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return Collections.emptyMap();
        try {
            String jsonStr = rawBody.trim();
            if (jsonStr.contains("data:")) {
                String[] lines = jsonStr.split("\n");
                for (String line : lines) {
                    if (line.startsWith("data:")) {
                        jsonStr = line.substring(5).trim();
                        break;
                    }
                }
            }
            return objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse MCP response JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    public Mono<McpServerDto> saveOAuthTokens(String serverId, String accessToken, String refreshToken, Long expiresInSeconds) {
        return mcpServerRepository.findById(serverId)
                .flatMap(server -> {
                    if (accessToken != null && !accessToken.isBlank()) {
                        server.setOauthAccessTokenEncrypted(cryptoService.encrypt(accessToken));
                    }
                    if (refreshToken != null && !refreshToken.isBlank()) {
                        server.setOauthRefreshTokenEncrypted(cryptoService.encrypt(refreshToken));
                    }
                    if (expiresInSeconds != null && expiresInSeconds > 0) {
                        server.setOauthExpiresAt(LocalDateTime.now().plusSeconds(expiresInSeconds));
                    }
                    server.setNewEntity(false);
                    return mcpServerRepository.save(server);
                })
                .map(this::toDto);
    }

    @SuppressWarnings("unchecked")
    public Flux<com.ppgpt.gateway.dto.ToolDto> getActiveTools() {
        return mcpServerRepository.findByIsActiveTrue()
                .flatMap(server -> {
                    WebClient.RequestBodySpec spec = aiWebClient.post()
                            .uri(server.getEndpointUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Accept", "application/json, text/event-stream");

                    if ("STATIC_KEY".equals(server.getAuthType()) && server.getApiKeyEncrypted() != null && !server.getApiKeyEncrypted().isBlank()) {
                        String rawKey = cryptoService.decrypt(server.getApiKeyEncrypted());
                        spec.header("Authorization", "Bearer " + rawKey);
                    } else if ("OAUTH2".equals(server.getAuthType()) && server.getOauthAccessTokenEncrypted() != null && !server.getOauthAccessTokenEncrypted().isBlank()) {
                        String rawToken = cryptoService.decrypt(server.getOauthAccessTokenEncrypted());
                        spec.header("Authorization", "Bearer " + rawToken);
                    }

                    Map<String, Object> jsonRpcBody = Map.of(
                            "jsonrpc", "2.0",
                            "method", "tools/list",
                            "id", 1
                    );

                    return spec.bodyValue(jsonRpcBody)
                            .retrieve()
                            .bodyToFlux(String.class)
                            .collectList()
                            .map(lines -> String.join("\n", lines))
                            .timeout(Duration.ofSeconds(3))
                            .flatMapMany(rawBody -> {
                                Map<String, Object> resp = parseJsonResponse(rawBody);
                                Map<String, Object> result = (Map<String, Object>) resp.get("result");
                                if (result != null && result.containsKey("tools")) {
                                    List<Map<String, Object>> toolsList = (List<Map<String, Object>>) result.get("tools");
                                    return Flux.fromIterable(toolsList)
                                            .map(t -> new com.ppgpt.gateway.dto.ToolDto(
                                                    "function",
                                                    new com.ppgpt.gateway.dto.ToolDto.FunctionDef(
                                                            (String) t.get("name"),
                                                            (String) t.get("description"),
                                                            (Map<String, Object>) t.get("inputSchema")
                                                    )
                                            ));
                                }
                                return Flux.empty();
                            })
                            .onErrorResume(e -> Flux.empty());
                });
    }

    @SuppressWarnings("unchecked")
    public Flux<com.ppgpt.gateway.dto.ToolDto> getActiveToolsForGroup(String groupId) {
        if (groupId == null || groupId.isBlank()) return getActiveTools();
        return getGroupToolAccess(groupId)
                .filter(McpToolDto::isAvailable)
                .filter(McpToolDto::isEnabledForGroup)
                .map(t -> {
                    Map<String, Object> inputSchemaMap = Collections.emptyMap();
                    if (t.getInputSchema() != null && !t.getInputSchema().isBlank()) {
                        try {
                            inputSchemaMap = objectMapper.readValue(t.getInputSchema(), Map.class);
                        } catch (Exception ignored) {}
                    }
                    return new com.ppgpt.gateway.dto.ToolDto(
                            "function",
                            new com.ppgpt.gateway.dto.ToolDto.FunctionDef(
                                    t.getNamespacedName(), // Guard 2: namespaced tool name
                                    t.getDescription(),
                                    inputSchemaMap
                            )
                    );
                });
    }

    @SuppressWarnings("unchecked")
    public Mono<String> executeTool(String namespacedOrToolName, Map<String, Object> arguments) {
        String serverPrefix = "";
        String actualToolName = namespacedOrToolName;

        if (namespacedOrToolName != null && namespacedOrToolName.contains("__")) {
            String[] parts = namespacedOrToolName.split("__", 2);
            serverPrefix = parts[0];
            actualToolName = parts[1];
        }

        final String finalToolName = actualToolName;
        final String finalPrefix = serverPrefix;

        return mcpServerRepository.findByIsActiveTrue()
                .filter(server -> {
                    if (finalPrefix.isBlank()) return true;
                    String cleanName = server.getName().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
                    return cleanName.equals(finalPrefix) || server.getName().equalsIgnoreCase(finalPrefix);
                })
                .flatMap(server -> {
                    WebClient.RequestBodySpec spec = aiWebClient.post()
                            .uri(server.getEndpointUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Accept", "application/json, text/event-stream");

                    if ("STATIC_KEY".equals(server.getAuthType()) && server.getApiKeyEncrypted() != null && !server.getApiKeyEncrypted().isBlank()) {
                        String rawKey = cryptoService.decrypt(server.getApiKeyEncrypted());
                        spec.header("Authorization", "Bearer " + rawKey);
                    } else if ("OAUTH2".equals(server.getAuthType()) && server.getOauthAccessTokenEncrypted() != null && !server.getOauthAccessTokenEncrypted().isBlank()) {
                        String rawToken = cryptoService.decrypt(server.getOauthAccessTokenEncrypted());
                        spec.header("Authorization", "Bearer " + rawToken);
                    }

                    Object postBody;
                    if ("NON_MCP_REST".equals(server.getCapabilityStatus())) {
                        postBody = arguments != null ? arguments : Map.of();
                    } else {
                        postBody = Map.of(
                                "jsonrpc", "2.0",
                                "method", "tools/call",
                                "params", Map.of(
                                        "name", finalToolName,
                                        "arguments", arguments != null ? arguments : Map.of()
                                ),
                                "id", 1
                        );
                    }

                    return spec.bodyValue(postBody)
                            .retrieve()
                            .bodyToFlux(String.class)
                            .collectList()
                            .map(lines -> String.join("\n", lines))
                            .timeout(Duration.ofSeconds(5))
                            .flatMap(rawBody -> {
                                try {
                                    Map<String, Object> resp = parseJsonResponse(rawBody);
                                    if (resp != null && resp.containsKey("result")) {
                                        Map<String, Object> result = (Map<String, Object>) resp.get("result");
                                        if (result != null && result.containsKey("content")) {
                                            List<Map<String, Object>> contentList = (List<Map<String, Object>>) result.get("content");
                                            if (contentList != null && !contentList.isEmpty()) {
                                                String text = (String) contentList.get(0).get("text");
                                                if (text != null) return Mono.just(text);
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {}
                                return Mono.just(rawBody);
                            })
                            .onErrorResume(e -> Mono.empty());
                })
                .next()
                // Guard 5: Graceful tool execution fallback
                .defaultIfEmpty("{\"error\": \"Tool '" + namespacedOrToolName + "' is currently unavailable or disabled by administrator.\"}");
    }

    private McpServerDto toDto(McpServer server) {
        return McpServerDto.builder()
                .id(server.getId())
                .name(server.getName())
                .endpointUrl(server.getEndpointUrl())
                .authType(server.getAuthType())
                .description(server.getDescription())
                .isActive(server.getIsActive())
                .hasApiKey(server.getApiKeyEncrypted() != null && !server.getApiKeyEncrypted().isBlank())
                .oauthAuthorizeUrl(server.getOauthAuthorizeUrl())
                .oauthTokenUrl(server.getOauthTokenUrl())
                .oauthClientId(server.getOauthClientId())
                .hasOAuthTokens(server.getOauthAccessTokenEncrypted() != null && !server.getOauthAccessTokenEncrypted().isBlank())
                .oauthExpiresAt(server.getOauthExpiresAt())
                .supportsTools(server.getSupportsTools() != null ? server.getSupportsTools() : true)
                .supportsResources(server.getSupportsResources() != null ? server.getSupportsResources() : false)
                .supportsPrompts(server.getSupportsPrompts() != null ? server.getSupportsPrompts() : false)
                .capabilityStatus(server.getCapabilityStatus() != null ? server.getCapabilityStatus() : "DISCOVERED")
                .createdAt(server.getCreatedAt())
                .build();
    }

    // ─── MCP Multi-Capability Discovery & Handshake ─────────────────────────

    @SuppressWarnings("unchecked")
    private Mono<McpServer> initializeHandshake(McpServer server) {
        WebClient.RequestBodySpec spec = prepareRequestSpec(server);
        Map<String, Object> jsonRpcBody = Map.of(
                "jsonrpc", "2.0",
                "method", "initialize",
                "params", Map.of(
                        "protocolVersion", "2024-11-05",
                        "clientInfo", Map.of("name", "PP-GPT Gateway", "version", "1.0.0"),
                        "capabilities", Map.of()
                ),
                "id", 1
        );

        return spec.bodyValue(jsonRpcBody)
                .retrieve()
                .bodyToFlux(String.class)
                .collectList()
                .map(lines -> String.join("\n", lines))
                .timeout(Duration.ofSeconds(4))
                .flatMap(rawBody -> {
                    try {
                        Map<String, Object> resp = parseJsonResponse(rawBody);
                        if (resp.containsKey("result")) {
                            Map<String, Object> result = (Map<String, Object>) resp.get("result");
                            if (result != null) {
                                Map<String, Object> caps = (Map<String, Object>) result.get("capabilities");
                                if (caps != null) {
                                    server.setSupportsTools(caps.containsKey("tools") || caps.isEmpty());
                                    server.setSupportsResources(caps.containsKey("resources"));
                                    server.setSupportsPrompts(caps.containsKey("prompts"));
                                } else {
                                    server.setSupportsTools(true);
                                    server.setSupportsResources(false);
                                    server.setSupportsPrompts(false);
                                }
                                server.setCapabilityStatus("DISCOVERED");

                                return sendInitializedNotification(server)
                                        .then(mcpServerRepository.save(server));
                            }
                        }
                    } catch (Exception e) {
                        log.debug("[MCP Handshake] Failed to parse initialize response for '{}'", server.getName());
                    }
                    server.setSupportsTools(true);
                    server.setCapabilityStatus("DISCOVERED");
                    return mcpServerRepository.save(server);
                })
                .onErrorResume(e -> {
                    log.info("[MCP Handshake] Server '{}' does not respond to 'initialize' (falling back to NON_MCP_REST / Manual mode).", server.getName());
                    server.setCapabilityStatus("NON_MCP_REST");
                    return mcpServerRepository.save(server);
                });
    }

    private Mono<Void> sendInitializedNotification(McpServer server) {
        WebClient.RequestBodySpec spec = prepareRequestSpec(server);
        Map<String, Object> notification = Map.of(
                "jsonrpc", "2.0",
                "method", "notifications/initialized"
        );
        return spec.bodyValue(notification)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(2))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private WebClient.RequestBodySpec prepareRequestSpec(McpServer server) {
        WebClient.RequestBodySpec spec = aiWebClient.post()
                .uri(server.getEndpointUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept", "application/json, text/event-stream");

        if ("STATIC_KEY".equals(server.getAuthType()) && server.getApiKeyEncrypted() != null && !server.getApiKeyEncrypted().isBlank()) {
            String rawKey = cryptoService.decrypt(server.getApiKeyEncrypted());
            spec.header("Authorization", "Bearer " + rawKey);
        } else if ("OAUTH2".equals(server.getAuthType()) && server.getOauthAccessTokenEncrypted() != null && !server.getOauthAccessTokenEncrypted().isBlank()) {
            String rawToken = cryptoService.decrypt(server.getOauthAccessTokenEncrypted());
            spec.header("Authorization", "Bearer " + rawToken);
        }
        return spec;
    }

    /**
     * Synchronizes tools, resources, and prompts for an MCP Server with vulnerability protections:
     * - Handshake (`initialize`) to discover protocol capabilities.
     * - Fallback handling for servers without `tools/list` (NON_MCP_REST).
     */
    private Mono<Boolean> pingLegacyEndpoint(McpServer server) {
        try {
            WebClient.RequestBodySpec spec = prepareRequestSpec(server);
            return spec.bodyValue(Map.of())
                    .exchangeToMono(response -> Mono.just(true))
                    .timeout(Duration.ofSeconds(3))
                    .onErrorReturn(false);
        } catch (Exception e) {
            return Mono.just(false);
        }
    }

    /**
     * Synchronize tools from MCP Server via `tools/list` JSON-RPC method.
     * Implements:
     * - Auto-discovery & Upsert of tool schema into database.
     * - Prompt injection guard on tool descriptions.
     * - Name collision prevention via server namespacing prefix (server_name__tool_name).
     * - Retry threshold protection (failed_sync_count >= 3) for network flakes.
     */
    @SuppressWarnings("unchecked")
    public Mono<List<McpToolDto>> syncTools(String serverId) {
        return mcpServerRepository.findById(serverId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP Server not found")))
                .flatMap(server -> {
                    if ("NON_MCP_REST".equals(server.getCapabilityStatus())) {
                        return Mono.just(server);
                    }
                    return initializeHandshake(server);
                })
                .flatMap(server -> {
                    if ("NON_MCP_REST".equals(server.getCapabilityStatus()) || Boolean.FALSE.equals(server.getSupportsTools())) {
                        log.info("[MCP Sync] Server '{}' operates in NON_MCP_REST/Manual mode. Verifying endpoint reachability...", server.getName());
                        return mcpToolRepository.findByMcpServerId(server.getId())
                                .collectList()
                                .flatMap(toolsList -> 
                                    pingLegacyEndpoint(server)
                                            .flatMap(isReachable -> {
                                                if (toolsList == null || toolsList.isEmpty()) {
                                                    return Mono.just(Collections.emptyList());
                                                }
                                                return Flux.fromIterable(toolsList)
                                                        .concatMap(tool -> {
                                                            tool.setFailedSyncCount(isReachable ? 0 : Math.max(3, tool.getFailedSyncCount() + 1));
                                                            tool.setAvailable(isReachable);
                                                            tool.setLastSyncedAt(LocalDateTime.now());
                                                            tool.setNewEntity(false);
                                                            return mcpToolRepository.save(tool);
                                                        })
                                                        .map(t -> toToolDto(t, server.getName(), false))
                                                        .collectList();
                                            })
                                );
                    }

                    WebClient.RequestBodySpec spec = prepareRequestSpec(server);
                    Map<String, Object> jsonRpcBody = Map.of(
                            "jsonrpc", "2.0",
                            "method", "tools/list",
                            "id", 1
                    );

                    return spec.bodyValue(jsonRpcBody)
                            .retrieve()
                            .bodyToFlux(String.class)
                            .collectList()
                            .map(lines -> String.join("\n", lines))
                            .timeout(Duration.ofSeconds(5)) // Guard 4: Timeout protection
                            .flatMap(rawBody -> {
                                Map<String, Object> resp = parseJsonResponse(rawBody);
                                Map<String, Object> result = (Map<String, Object>) resp.get("result");
                                List<Map<String, Object>> toolsList = Collections.emptyList();
                                if (result != null && result.containsKey("tools")) {
                                    toolsList = (List<Map<String, Object>>) result.get("tools");
                                }
                                return processSyncSuccess(server, toolsList);
                            })
                            .onErrorResume(e -> {
                                log.warn("[MCP Sync] Network failure/timeout for server '{}': {}", server.getName(), e.getMessage());
                                return processSyncFailure(server);
                            });
                });
    }

    public Flux<McpToolDto> syncAllTools() {
        return mcpServerRepository.findByIsActiveTrue()
                .flatMap(server -> syncTools(server.getId()).flatMapMany(Flux::fromIterable));
    }

    @SuppressWarnings("unchecked")
    private Mono<List<McpToolDto>> processSyncSuccess(McpServer server, List<Map<String, Object>> toolsList) {
        LocalDateTime now = LocalDateTime.now();
        List<String> currentToolNames = toolsList.stream()
                .map(t -> (String) t.get("name"))
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toList());

        // 1. Process returned tools (Upsert)
        Flux<McpTool> upsertedFlux = Flux.fromIterable(toolsList)
                .flatMap(t -> {
                    String name = (String) t.get("name");
                    if (name == null || name.isBlank()) return Mono.empty();

                    String rawDesc = (String) t.get("description");
                    String sanitizedDesc = sanitizeDescription(rawDesc); // Guard 3: Prompt injection guard
                    String namespaced = computeNamespacedName(server.getName(), name); // Guard 2: Name collision guard

                    String schemaJson = "";
                    try {
                        Object inputSchemaObj = t.get("inputSchema");
                        if (inputSchemaObj != null) {
                            schemaJson = objectMapper.writeValueAsString(inputSchemaObj);
                        }
                    } catch (Exception ex) {
                        schemaJson = "{}";
                    }

                    final String finalSchema = schemaJson;

                    return mcpToolRepository.findByMcpServerIdAndToolName(server.getId(), name)
                            .flatMap(existing -> {
                                existing.setNamespacedName(namespaced);
                                existing.setDescription(sanitizedDesc);
                                existing.setInputSchema(finalSchema);
                                existing.setAvailable(true);
                                existing.setFailedSyncCount(0); // Reset failure threshold
                                existing.setLastSyncedAt(now);
                                existing.setNewEntity(false);
                                return mcpToolRepository.save(existing);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                McpTool newTool = McpTool.builder()
                                        .id(UUID.randomUUID().toString())
                                        .mcpServerId(server.getId())
                                        .toolName(name)
                                        .namespacedName(namespaced)
                                        .description(sanitizedDesc)
                                        .inputSchema(finalSchema)
                                        .isAvailable(true)
                                        .failedSyncCount(0)
                                        .lastSyncedAt(now)
                                        .createdAt(now)
                                        .isNewEntity(true)
                                        .build();
                                return mcpToolRepository.save(newTool);
                            }));
                });

        // 2. Handle missing tools (Increment failed_sync_count; prune if >= 3)
        Mono<Void> pruningsMono = mcpToolRepository.findByMcpServerId(server.getId())
                .filter(existing -> !currentToolNames.contains(existing.getToolName()))
                .flatMap(removed -> {
                    int newCount = removed.getFailedSyncCount() + 1;
                    removed.setFailedSyncCount(newCount);
                    removed.setNewEntity(false);
                    if (newCount >= 3) {
                        // Mark unavailable and remove from group access
                        removed.setAvailable(false);
                        return groupMcpToolAccessRepository.deleteByMcpToolId(removed.getId())
                                .then(mcpToolRepository.save(removed));
                    } else {
                        return mcpToolRepository.save(removed);
                    }
                })
                .then();

        return upsertedFlux.collectList()
                .delayUntil(list -> pruningsMono)
                .map(list -> list.stream().map(t -> toToolDto(t, server.getName(), false)).collect(Collectors.toList()));
    }

    private Mono<List<McpToolDto>> processSyncFailure(McpServer server) {
        // Network flake / timeout: Increment failed_sync_count for all server tools; prune only if >= 3
        return mcpToolRepository.findByMcpServerId(server.getId())
                .flatMap(tool -> {
                    int newCount = tool.getFailedSyncCount() + 1;
                    tool.setFailedSyncCount(newCount);
                    tool.setNewEntity(false);
                    if (newCount >= 3) {
                        tool.setAvailable(false);
                        return groupMcpToolAccessRepository.deleteByMcpToolId(tool.getId())
                                .then(mcpToolRepository.save(tool));
                    }
                    return mcpToolRepository.save(tool);
                })
                .map(t -> toToolDto(t, server.getName(), false))
                .collectList();
    }

    public Flux<McpToolDto> getDiscoveredTools(String serverId) {
        return mcpServerRepository.findById(serverId)
                .flatMapMany(server -> mcpToolRepository.findByMcpServerId(serverId)
                        .map(t -> toToolDto(t, server.getName(), false)));
    }

    public Flux<McpToolDto> getGroupToolAccess(String groupId) {
        return mcpServerRepository.findByIsActiveTrue()
                .collectMap(McpServer::getId, McpServer::getName)
                .flatMapMany(serverMap -> 
                    groupMcpToolAccessRepository.findByGroupId(groupId)
                        .collectMap(GroupMcpToolAccess::getMcpToolId, GroupMcpToolAccess::isEnabled)
                        .flatMapMany(enabledMap -> 
                            mcpToolRepository.findAll()
                                .map(t -> {
                                    String srvName = serverMap.getOrDefault(t.getMcpServerId(), "Unknown Server");
                                    boolean enabled = enabledMap.getOrDefault(t.getId(), true); // Default enabled if available
                                    return toToolDto(t, srvName, enabled);
                                })
                        )
                );
    }

    public Mono<Void> updateGroupToolAccess(String groupId, List<GroupToolAccessRequest> requests) {
        if (requests == null || requests.isEmpty()) return Mono.empty();
        return Flux.fromIterable(requests)
                .flatMap(req -> 
                    groupMcpToolAccessRepository.findByGroupIdAndMcpToolId(groupId, req.getMcpToolId())
                        .flatMap(existing -> {
                            existing.setEnabled(req.isEnabled());
                            existing.setNewEntity(false);
                            return groupMcpToolAccessRepository.save(existing);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            GroupMcpToolAccess newAccess = GroupMcpToolAccess.builder()
                                    .id(UUID.randomUUID().toString())
                                    .groupId(groupId)
                                    .mcpToolId(req.getMcpToolId())
                                    .isEnabled(req.isEnabled())
                                    .isNewEntity(true)
                                    .build();
                            return groupMcpToolAccessRepository.save(newAccess);
                        }))
                )
                .then();
    }

    private String sanitizeDescription(String input) {
        if (input == null) return "";
        String cleaned = input.replaceAll("(?i)(ignore\\s+all\\s+previous\\s+instructions|system\\s+override|system\\s+prompt\\s+override|reveal\\s+admin\\s+password|ignore\\s+safety\\s+rules)", "[FILTERED]");
        if (cleaned.length() > 1000) {
            cleaned = cleaned.substring(0, 1000) + "...";
        }
        return cleaned;
    }

    public Mono<McpToolDto> createManualTool(String serverId, CreateManualToolRequest request) {
        return mcpServerRepository.findById(serverId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP Server not found")))
                .flatMap(server -> {
                    server.setCapabilityStatus("NON_MCP_REST");
                    server.setSupportsTools(true);
                    return mcpServerRepository.save(server)
                            .flatMap(savedServer -> {
                                String name = request.getToolName().trim();
                                String sanitizedDesc = sanitizeDescription(request.getDescription());
                                String namespaced = computeNamespacedName(savedServer.getName(), name);
                                String schema = (request.getInputSchema() != null && !request.getInputSchema().isBlank()) ? request.getInputSchema() : "{}";

                                return mcpToolRepository.findByMcpServerIdAndToolName(serverId, name)
                                        .flatMap(existing -> {
                                            existing.setNamespacedName(namespaced);
                                            existing.setDescription(sanitizedDesc);
                                            existing.setInputSchema(schema);
                                            existing.setAvailable(true);
                                            existing.setFailedSyncCount(0);
                                            existing.setLastSyncedAt(LocalDateTime.now());
                                            existing.setNewEntity(false);
                                            return mcpToolRepository.save(existing);
                                        })
                                        .switchIfEmpty(Mono.defer(() -> {
                                            McpTool newTool = McpTool.builder()
                                                    .id(UUID.randomUUID().toString())
                                                    .mcpServerId(serverId)
                                                    .toolName(name)
                                                    .namespacedName(namespaced)
                                                    .description(sanitizedDesc)
                                                    .inputSchema(schema)
                                                    .isAvailable(true)
                                                    .failedSyncCount(0)
                                                    .lastSyncedAt(LocalDateTime.now())
                                                    .createdAt(LocalDateTime.now())
                                                    .isNewEntity(true)
                                                    .build();
                                            return mcpToolRepository.save(newTool);
                                        }))
                                        .map(tool -> toToolDto(tool, savedServer.getName(), true));
                            });
                });
    }

    public Mono<Void> deleteManualTool(String serverId, String toolId) {
        return mcpToolRepository.findById(toolId)
                .filter(tool -> tool.getMcpServerId().equals(serverId))
                .flatMap(tool -> groupMcpToolAccessRepository.deleteByMcpToolId(toolId)
                        .then(mcpToolRepository.delete(tool)));
    }

    public Mono<List<McpToolDto>> importOpenApiSpec(String serverId, OpenApiImportRequest request) {
        return mcpServerRepository.findById(serverId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP Server not found")))
                .flatMap(server -> {
                    server.setCapabilityStatus("NON_MCP_REST");
                    server.setSupportsTools(true);
                    return mcpServerRepository.save(server)
                            .flatMap(savedServer -> {
                                List<CreateManualToolRequest> parsedTools = parseOpenApiPaths(request.getOpenApiSpec());
                                return Flux.fromIterable(parsedTools)
                                        .flatMap(toolReq -> createManualTool(serverId, toolReq))
                                        .collectList();
                            });
                });
    }

    public Flux<McpResource> getDiscoveredResources(String serverId) {
        return mcpResourceRepository.findByMcpServerId(serverId);
    }

    public Flux<McpPrompt> getDiscoveredPrompts(String serverId) {
        return mcpPromptRepository.findByMcpServerId(serverId);
    }

    private List<CreateManualToolRequest> parseOpenApiPaths(String openApiSpec) {
        List<CreateManualToolRequest> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(openApiSpec);
            JsonNode paths = root.get("paths");
            if (paths != null && paths.isObject()) {
                paths.fieldNames().forEachRemaining(path -> {
                    JsonNode pathNode = paths.get(path);
                    if (pathNode != null && pathNode.isObject()) {
                        pathNode.fieldNames().forEachRemaining(method -> {
                            if (List.of("get", "post", "put", "delete", "patch").contains(method.toLowerCase())) {
                                JsonNode opNode = pathNode.get(method);
                                String opId = opNode.has("operationId") ? opNode.get("operationId").asText() : "";
                                String summary = opNode.has("summary") ? opNode.get("summary").asText() : (opNode.has("description") ? opNode.get("description").asText() : "");
                                String toolName = !opId.isBlank() ? opId : (method.toLowerCase() + "_" + path.replaceAll("[^a-zA-Z0-9]", "_"));

                                toolName = toolName.replaceAll("^_+|_+$", "").replaceAll("_+", "_");

                                CreateManualToolRequest req = CreateManualToolRequest.builder()
                                        .toolName(toolName)
                                        .description(summary.isBlank() ? "OpenAPI endpoint " + method.toUpperCase() + " " + path : summary)
                                        .inputSchema("{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"default\":\"" + path + "\"},\"method\":{\"type\":\"string\",\"default\":\"" + method.toUpperCase() + "\"}}}")
                                        .build();
                                result.add(req);
                            }
                        });
                    }
                });
            }
        } catch (Exception e) {
            log.warn("[OpenAPI Parser] Failed to parse spec: {}", e.getMessage());
            CreateManualToolRequest fallback = CreateManualToolRequest.builder()
                    .toolName("custom_api_endpoint")
                    .description("Imported Custom API Endpoint")
                    .inputSchema("{}")
                    .build();
            result.add(fallback);
        }
        return result;
    }

    // Guard 2 helper: Unique namespacing
    private String computeNamespacedName(String serverName, String toolName) {
        String cleanServer = serverName.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        return cleanServer + "__" + toolName;
    }

    private McpToolDto toToolDto(McpTool tool, String serverName, boolean isEnabled) {
        return McpToolDto.builder()
                .id(tool.getId())
                .mcpServerId(tool.getMcpServerId())
                .mcpServerName(serverName)
                .toolName(tool.getToolName())
                .namespacedName(tool.getNamespacedName())
                .description(tool.getDescription())
                .inputSchema(tool.getInputSchema())
                .isAvailable(tool.isAvailable())
                .failedSyncCount(tool.getFailedSyncCount())
                .isEnabledForGroup(isEnabled)
                .lastSyncedAt(tool.getLastSyncedAt())
                .createdAt(tool.getCreatedAt())
                .build();
    }
}

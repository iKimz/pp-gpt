package com.ppgpt.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppgpt.gateway.domain.GroupMcpToolAccess;
import com.ppgpt.gateway.domain.McpPrompt;
import com.ppgpt.gateway.domain.McpResource;
import com.ppgpt.gateway.domain.McpServer;
import com.ppgpt.gateway.domain.McpTool;
import com.ppgpt.gateway.dto.CreateManualToolRequest;
import com.ppgpt.gateway.dto.CreateMcpServerRequest;
import com.ppgpt.gateway.dto.GroupToolAccessRequest;
import com.ppgpt.gateway.dto.McpServerDto;
import com.ppgpt.gateway.dto.McpToolDto;
import com.ppgpt.gateway.dto.OpenApiImportRequest;
import com.ppgpt.gateway.dto.ToolDto;
import com.ppgpt.gateway.repository.GroupMcpToolAccessRepository;
import com.ppgpt.gateway.repository.McpPromptRepository;
import com.ppgpt.gateway.repository.McpResourceRepository;
import com.ppgpt.gateway.repository.McpServerRepository;
import com.ppgpt.gateway.repository.McpToolRepository;
import com.ppgpt.gateway.util.JsonUtil;
import com.ppgpt.gateway.util.McpConstants;
import com.ppgpt.gateway.util.SecuritySanitizerUtil;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

/**
 * Service managing Model Context Protocol (MCP) server lifecycle, protocol capability discovery,
 * dynamic OAuth2 metadata discovery, agentic tool execution, and Legacy REST manual tool fallbacks.
 */
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

    @org.springframework.beans.factory.annotation.Value("${app.mcp.oauth-redirect-uri:http://localhost/api/v1/mcp/oauth/callback}")
    private String defaultOAuthRedirectUri;

    /**
     * DTO representing discovered OAuth2 authorization, token, and dynamic registration metadata.
     */
    public static class OAuthDiscoveryResult {
        public final String authorizeUrl;
        public final String tokenUrl;
        public final String registrationUrl;

        public OAuthDiscoveryResult(String authorizeUrl, String tokenUrl, String registrationUrl) {
            this.authorizeUrl = authorizeUrl;
            this.tokenUrl = tokenUrl;
            this.registrationUrl = registrationUrl;
        }
    }

    /**
     * Discovers OAuth2 metadata endpoints using RFC 9207 / RFC 8414 well-known metadata discovery.
     *
     * @param wwwAuthHeader Raw WWW-Authenticate header string from 401/403 HTTP response
     * @return Mono emitting discovered OAuth endpoints or empty Mono
     */
    private Mono<OAuthDiscoveryResult> discoverOAuthMetadata(String wwwAuthHeader) {
        if (wwwAuthHeader == null || wwwAuthHeader.isBlank()) {
            return Mono.empty();
        }

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
                    .bodyToMono(String.class)
                    .flatMap(metaBody -> {
                        Map<String, Object> meta = JsonUtil.parseJsonMap(metaBody);
                        List<?> authServers = (List<?>) meta.get("authorization_servers");
                        if (authServers != null && !authServers.isEmpty()) {
                            String authServerBase = String.valueOf(authServers.get(0)).replaceAll("/+$", "");
                            String discoveryUrl = authServerBase + "/.well-known/oauth-authorization-server";
                            return aiWebClient.get()
                                    .uri(discoveryUrl)
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .map(discBody -> {
                                        Map<String, Object> disc = JsonUtil.parseJsonMap(discBody);
                                        return new OAuthDiscoveryResult(
                                                (String) disc.get("authorization_endpoint"),
                                                (String) disc.get("token_endpoint"),
                                                (String) disc.get("registration_endpoint")
                                        );
                                    });
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

    /**
     * Executes RFC 7591 Dynamic Client Registration to register Gateway as an OAuth client.
     *
     * @param registrationUrl Registration endpoint URL
     * @param redirectUri     Callback URL
     * @return Mono emitting registered client_id or empty Mono
     */
    private Mono<String> registerDynamicClient(String registrationUrl, String redirectUri) {
        if (registrationUrl == null || registrationUrl.isBlank()) {
            return Mono.empty();
        }

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
                .bodyToMono(String.class)
                .map(respBody -> {
                    Map<String, Object> resp = JsonUtil.parseJsonMap(respBody);
                    return (String) resp.get("client_id");
                })
                .onErrorResume(e -> {
                    log.warn("[MCP OAuth] Dynamic client registration at {} failed: {}", registrationUrl, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Retrieves all registered MCP server definitions.
     *
     * @return Flux of MCP server DTOs
     */
    public Flux<McpServerDto> getAllMcpServers() {
        return mcpServerRepository.findAll()
                .map(this::toDto);
    }

    /**
     * Creates a new MCP server definition in database.
     *
     * @param request Create request containing endpoint URL, auth type, and credentials
     * @return Mono emitting created MCP server DTO
     */
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
                .authType(request.getAuthType() != null ? request.getAuthType() : McpConstants.AUTH_STATIC_KEY)
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
                .capabilityStatus(McpConstants.CAPABILITY_DISCOVERED)
                .createdAt(LocalDateTime.now())
                .newEntity(true)
                .build();

        return mcpServerRepository.save(server)
                .map(this::toDto);
    }

    /**
     * Updates an existing MCP server configuration.
     *
     * @param id      Server ID
     * @param request Update request payload
     * @return Mono emitting updated MCP server DTO
     */
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

    /**
     * Deletes an MCP server and its associated definitions from database.
     *
     * @param id Server ID
     * @return Mono completing upon deletion
     */
    public Mono<Void> deleteMcpServer(String id) {
        return mcpServerRepository.deleteById(id);
    }

    /**
     * Tests connectivity to an MCP server, performing OAuth2 auto-discovery on 401/403 errors.
     *
     * @param id Server ID
     * @return Mono emitting connection status map
     */
    public Mono<Map<String, Object>> testConnection(String id) {
        return mcpServerRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP Server not found")))
                .flatMap(server -> {
                    WebClient.RequestBodySpec spec = prepareRequestSpec(server);

                    Map<String, Object> jsonRpcBody = Map.of(
                            "jsonrpc", "2.0",
                            "method", McpConstants.METHOD_TOOLS_LIST,
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

                                                server.setAuthType(McpConstants.AUTH_OAUTH2);
                                                if (authUrl != null) server.setOauthAuthorizeUrl(authUrl);
                                                if (tokenUrl != null) server.setOauthTokenUrl(tokenUrl);

                                                if ((server.getOauthClientId() == null || server.getOauthClientId().isBlank()) && regUrl != null) {
                                                    String redirectUri = defaultOAuthRedirectUri;
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
            return JsonUtil.parseJsonMap(jsonStr);
        } catch (Exception e) {
            log.warn("Failed to parse MCP response JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Stores OAuth2 access and refresh tokens for an MCP server.
     *
     * @param serverId         Server ID
     * @param accessToken      Decrypted access token string
     * @param refreshToken     Decrypted refresh token string
     * @param expiresInSeconds Lifetime in seconds
     * @return Mono emitting updated MCP server DTO
     */
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

    /**
     * Retrieves active, authorized MCP tools for a user group formatted as ToolDto.
     *
     * @param groupId User group ID
     * @return Flux of ToolDto entities for LLM completion request
     */
    public Flux<ToolDto> getActiveToolsForGroup(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return Flux.empty();
        }

        return groupMcpToolAccessRepository.findByGroupIdAndIsEnabledTrue(groupId)
                .map(GroupMcpToolAccess::getMcpToolId)
                .collectList()
                .flatMapMany(enabledToolIds -> {
                    if (enabledToolIds.isEmpty()) {
                        return Flux.empty();
                    }
                    return mcpToolRepository.findByIdInAndIsAvailableTrue(enabledToolIds)
                            .map(t -> {
                                Map<String, Object> schemaMap = JsonUtil.parseJsonMap(t.getInputSchema());
                                ToolDto.FunctionDef funcDef = new ToolDto.FunctionDef(
                                        t.getNamespacedName(),
                                        t.getDescription() != null ? t.getDescription() : "",
                                        schemaMap.isEmpty() ? Map.of("type", "object", "properties", Map.of()) : schemaMap
                                );
                                return new ToolDto("function", funcDef);
                            });
                });
    }

    /**
     * Executes a tool invocation request against an MCP or Legacy REST server.
     *
     * @param namespacedOrToolName Namespaced tool identifier (e.g. server_name__tool_name)
     * @param arguments            Execution arguments map
     * @return Mono emitting tool execution text result or error payload
     */
    public Mono<String> executeTool(String namespacedOrToolName, Map<String, Object> arguments) {
        if (namespacedOrToolName == null || namespacedOrToolName.isBlank()) {
            return Mono.just("{\"error\": \"Invalid tool name\"}");
        }

        String serverPrefix = "";
        String actualToolName = namespacedOrToolName;

        if (namespacedOrToolName.contains("__")) {
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
                    boolean isLegacyRest = McpConstants.CAPABILITY_NON_MCP_REST.equals(server.getCapabilityStatus());

                    return mcpToolRepository.findByMcpServerId(server.getId())
                            .filter(tool -> tool.getToolName().equalsIgnoreCase(finalToolName)
                                    || tool.getNamespacedName().equalsIgnoreCase(namespacedOrToolName))
                            .next()
                            .defaultIfEmpty(new McpTool())
                            .flatMap(mcpTool -> {
                                HttpMethod httpMethod = HttpMethod.POST;
                                String targetUrl = server.getEndpointUrl();
                                Map<String, String> customHeaders = new HashMap<>();

                                if (isLegacyRest) {
                                    String inputSchemaStr = mcpTool.getInputSchema();
                                    if (inputSchemaStr != null && !inputSchemaStr.isBlank()) {
                                        try {
                                            JsonNode schemaNode = objectMapper.readTree(inputSchemaStr);
                                            JsonNode properties = schemaNode.get("properties");
                                            if (properties != null && properties.isObject()) {
                                                if (properties.has("method") && properties.get("method").has("default")) {
                                                    String methodStr = properties.get("method").get("default").asText("POST");
                                                    try {
                                                        httpMethod = HttpMethod.valueOf(methodStr.toUpperCase());
                                                    } catch (Exception ignored) {}
                                                }

                                                if (properties.has("path") && properties.get("path").has("default")) {
                                                    String subPath = properties.get("path").get("default").asText("");
                                                    if (subPath != null && subPath.startsWith("/")) {
                                                        targetUrl = targetUrl.replaceAll("/+$", "") + subPath;
                                                    }
                                                }

                                                if (properties.has("headers") && properties.get("headers").has("default")) {
                                                    JsonNode headersNode = properties.get("headers").get("default");
                                                    if (headersNode != null && headersNode.isObject()) {
                                                        headersNode.fieldNames().forEachRemaining(key -> {
                                                            customHeaders.put(key, headersNode.get(key).asText());
                                                        });
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            log.warn("[Legacy REST Execute] Failed to parse inputSchema for tool {}: {}", namespacedOrToolName, e.getMessage());
                                        }
                                    }

                                    if (arguments != null) {
                                        if (arguments.containsKey("method")) {
                                            try {
                                                httpMethod = HttpMethod.valueOf(String.valueOf(arguments.get("method")).toUpperCase());
                                            } catch (Exception ignored) {}
                                        }
                                        if (arguments.containsKey("path")) {
                                            String subPath = String.valueOf(arguments.get("path"));
                                            if (subPath != null && subPath.startsWith("/")) {
                                                targetUrl = server.getEndpointUrl().replaceAll("/+$", "") + subPath;
                                            }
                                        }
                                        if (arguments.get("headers") instanceof Map<?, ?> argHeaders) {
                                            argHeaders.forEach((k, v) -> {
                                                if (k != null && v != null) customHeaders.put(String.valueOf(k), String.valueOf(v));
                                            });
                                        }
                                    }
                                }

                                WebClient.RequestBodySpec spec = aiWebClient.method(httpMethod)
                                        .uri(targetUrl)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("Accept", "application/json, text/event-stream, text/plain, */*");

                                applyAuthHeaders(spec, server);
                                if (!customHeaders.isEmpty()) {
                                    spec.headers(h -> customHeaders.forEach(h::set));
                                }

                                Object postBody = null;
                                if (isLegacyRest) {
                                    if (httpMethod != HttpMethod.GET) {
                                        if (arguments != null && arguments.containsKey("payload")) {
                                            postBody = arguments.get("payload");
                                        } else {
                                            postBody = arguments != null ? arguments : Map.of();
                                        }
                                    }
                                } else {
                                    postBody = Map.of(
                                            "jsonrpc", "2.0",
                                            "method", McpConstants.METHOD_TOOLS_CALL,
                                            "params", Map.of(
                                                    "name", finalToolName,
                                                    "arguments", arguments != null ? arguments : Map.of()
                                            ),
                                            "id", 1
                                    );
                                }

                                String finalTargetUrl = targetUrl;
                                WebClient.RequestHeadersSpec<?> headersSpec = (postBody != null) ? spec.bodyValue(postBody) : spec;

                                return headersSpec.retrieve()
                                        .bodyToFlux(String.class)
                                        .collectList()
                                        .map(lines -> String.join("\n", lines))
                                        .timeout(Duration.ofSeconds(30))
                                        .flatMap(rawBody -> {
                                            try {
                                                Map<String, Object> resp = parseJsonResponse(rawBody);
                                                if (resp != null && resp.containsKey("result")) {
                                                    @SuppressWarnings("unchecked")
                                                    Map<String, Object> result = (Map<String, Object>) resp.get("result");
                                                    if (result != null && result.containsKey("content")) {
                                                        @SuppressWarnings("unchecked")
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
                                        .onErrorResume(e -> {
                                            log.error("[Tool Execution Failed] Tool '{}' failed: {}", namespacedOrToolName, e.getMessage(), e);
                                            return Mono.just("{\"error\": \"Failed to execute tool '" + namespacedOrToolName + "': " + extractExceptionDetails(e, finalTargetUrl) + "\"}");
                                        });
                            });
                })
                .next()
                .defaultIfEmpty("{\"error\": \"Tool '" + namespacedOrToolName + "' is currently unavailable or disabled by administrator.\"}");
    }

    private void applyAuthHeaders(WebClient.RequestBodySpec spec, McpServer server) {
        if (McpConstants.AUTH_STATIC_KEY.equals(server.getAuthType()) && server.getApiKeyEncrypted() != null && !server.getApiKeyEncrypted().isBlank()) {
            String rawKey = cryptoService.decrypt(server.getApiKeyEncrypted());
            spec.headers(h -> h.set(McpConstants.HEADER_AUTHORIZATION, McpConstants.HEADER_BEARER_PREFIX + rawKey));
        } else if (McpConstants.AUTH_OAUTH2.equals(server.getAuthType()) && server.getOauthAccessTokenEncrypted() != null && !server.getOauthAccessTokenEncrypted().isBlank()) {
            String rawToken = cryptoService.decrypt(server.getOauthAccessTokenEncrypted());
            spec.headers(h -> h.set(McpConstants.HEADER_AUTHORIZATION, McpConstants.HEADER_BEARER_PREFIX + rawToken));
        }
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
                .capabilityStatus(server.getCapabilityStatus() != null ? server.getCapabilityStatus() : McpConstants.CAPABILITY_DISCOVERED)
                .createdAt(server.getCreatedAt())
                .build();
    }

    private Mono<McpServer> initializeHandshake(McpServer server) {
        WebClient.RequestBodySpec spec = prepareRequestSpec(server);
        Map<String, Object> jsonRpcBody = Map.of(
                "jsonrpc", "2.0",
                "method", McpConstants.METHOD_INITIALIZE,
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
                            @SuppressWarnings("unchecked")
                            Map<String, Object> result = (Map<String, Object>) resp.get("result");
                            if (result != null) {
                                @SuppressWarnings("unchecked")
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
                                server.setCapabilityStatus(McpConstants.CAPABILITY_DISCOVERED);

                                return sendInitializedNotification(server)
                                        .then(mcpServerRepository.save(server));
                            }
                        }
                    } catch (Exception e) {
                        log.debug("[MCP Handshake] Failed to parse initialize response for '{}'", server.getName());
                    }
                    server.setSupportsTools(true);
                    server.setCapabilityStatus(McpConstants.CAPABILITY_DISCOVERED);
                    return mcpServerRepository.save(server);
                })
                .onErrorResume(e -> {
                    log.info("[MCP Handshake] Server '{}' does not respond to 'initialize' (falling back to NON_MCP_REST / Manual mode).", server.getName());
                    server.setCapabilityStatus(McpConstants.CAPABILITY_NON_MCP_REST);
                    return mcpServerRepository.save(server);
                });
    }

    private Mono<Void> sendInitializedNotification(McpServer server) {
        WebClient.RequestBodySpec spec = prepareRequestSpec(server);
        Map<String, Object> notification = Map.of(
                "jsonrpc", "2.0",
                "method", McpConstants.METHOD_NOTIFICATIONS_INITIALIZED
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

        applyAuthHeaders(spec, server);
        return spec;
    }

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
     * Synchronizes tools from an MCP server or verifies reachability for Legacy REST endpoints.
     *
     * @param serverId Server ID
     * @return Mono emitting list of synchronized tool DTOs
     */
    public Mono<List<McpToolDto>> syncTools(String serverId) {
        return mcpServerRepository.findById(serverId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP Server not found")))
                .flatMap(server -> {
                    if (McpConstants.CAPABILITY_NON_MCP_REST.equals(server.getCapabilityStatus())) {
                        return Mono.just(server);
                    }
                    return initializeHandshake(server);
                })
                .flatMap(server -> {
                    if (McpConstants.CAPABILITY_NON_MCP_REST.equals(server.getCapabilityStatus()) || Boolean.FALSE.equals(server.getSupportsTools())) {
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
                            "method", McpConstants.METHOD_TOOLS_LIST,
                            "id", 1
                    );

                    return spec.bodyValue(jsonRpcBody)
                            .retrieve()
                            .bodyToFlux(String.class)
                            .collectList()
                            .map(lines -> String.join("\n", lines))
                            .timeout(Duration.ofSeconds(5))
                            .flatMap(rawBody -> {
                                Map<String, Object> resp = parseJsonResponse(rawBody);
                                @SuppressWarnings("unchecked")
                                Map<String, Object> result = (Map<String, Object>) resp.get("result");
                                List<Map<String, Object>> toolsList = Collections.emptyList();
                                if (result != null && result.containsKey("tools")) {
                                    @SuppressWarnings("unchecked")
                                    List<Map<String, Object>> castList = (List<Map<String, Object>>) result.get("tools");
                                    toolsList = castList;
                                }
                                return processSyncSuccess(server, toolsList);
                            })
                            .onErrorResume(e -> {
                                log.warn("[MCP Sync] Network failure/timeout for server '{}': {}", server.getName(), e.getMessage());
                                return processSyncFailure(server);
                            });
                });
    }

    /**
     * Synchronizes tools across all active MCP servers.
     *
     * @return Flux of synchronized tool DTOs
     */
    public Flux<McpToolDto> syncAllTools() {
        return mcpServerRepository.findByIsActiveTrue()
                .flatMap(server -> syncTools(server.getId()).flatMapMany(Flux::fromIterable));
    }

    private Mono<List<McpToolDto>> processSyncSuccess(McpServer server, List<Map<String, Object>> toolsList) {
        LocalDateTime now = LocalDateTime.now();
        List<String> currentToolNames = toolsList.stream()
                .map(t -> (String) t.get("name"))
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toList());

        Flux<McpTool> upsertedFlux = Flux.fromIterable(toolsList)
                .flatMap(t -> {
                    String name = (String) t.get("name");
                    if (name == null || name.isBlank()) return Mono.empty();

                    String rawDesc = (String) t.get("description");
                    String sanitizedDesc = SecuritySanitizerUtil.sanitizeToolDescription(rawDesc);
                    String namespaced = computeNamespacedName(server.getName(), name);

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
                                existing.setFailedSyncCount(0);
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

        Mono<Void> pruningsMono = mcpToolRepository.findByMcpServerId(server.getId())
                .filter(existing -> !currentToolNames.contains(existing.getToolName()))
                .flatMap(removed -> {
                    int newCount = removed.getFailedSyncCount() + 1;
                    removed.setFailedSyncCount(newCount);
                    removed.setNewEntity(false);
                    if (newCount >= 3) {
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

    /**
     * Retrieves all discovered tools for a given server ID.
     *
     * @param serverId Server ID
     * @return Flux of tool DTOs
     */
    public Flux<McpToolDto> getDiscoveredTools(String serverId) {
        return mcpServerRepository.findById(serverId)
                .flatMapMany(server -> {
                    boolean isManual = McpConstants.CAPABILITY_NON_MCP_REST.equals(server.getCapabilityStatus());
                    return mcpToolRepository.findByMcpServerId(serverId)
                            .map(t -> toToolDto(t, server.getName(), false, isManual));
                });
    }

    /**
     * Retrieves group tool access status for all tools given a group ID.
     *
     * @param groupId Group ID
     * @return Flux of tool DTOs with group authorization status
     */
    public Flux<McpToolDto> getGroupToolAccess(String groupId) {
        return mcpServerRepository.findByIsActiveTrue()
                .collectMap(McpServer::getId, s -> s)
                .flatMapMany(serverMap -> 
                    groupMcpToolAccessRepository.findByGroupId(groupId)
                        .collectMap(GroupMcpToolAccess::getMcpToolId, GroupMcpToolAccess::isEnabled)
                        .flatMapMany(enabledMap -> 
                            mcpToolRepository.findAll()
                                .map(t -> {
                                    McpServer srv = serverMap.get(t.getMcpServerId());
                                    String srvName = srv != null ? srv.getName() : "Unknown Server";
                                    boolean isManual = srv != null && McpConstants.CAPABILITY_NON_MCP_REST.equals(srv.getCapabilityStatus());
                                    boolean enabled = enabledMap.getOrDefault(t.getId(), true);
                                    return toToolDto(t, srvName, enabled, isManual);
                                })
                        )
                );
    }

    /**
     * Updates group tool access permissions.
     *
     * @param groupId  Group ID
     * @param requests List of tool access permission requests
     * @return Mono completing upon update
     */
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

    /**
     * Creates a manual tool definition for legacy REST endpoints.
     *
     * @param serverId Server ID
     * @param request  Manual tool request
     * @return Mono emitting created tool DTO
     */
    public Mono<McpToolDto> createManualTool(String serverId, CreateManualToolRequest request) {
        return mcpServerRepository.findById(serverId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP Server not found")))
                .flatMap(server -> {
                    server.setCapabilityStatus(McpConstants.CAPABILITY_NON_MCP_REST);
                    server.setSupportsTools(true);
                    return mcpServerRepository.save(server)
                            .flatMap(savedServer -> {
                                String name = request.getToolName().trim();
                                String sanitizedDesc = SecuritySanitizerUtil.sanitizeToolDescription(request.getDescription());
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

    /**
     * Updates an existing manual tool definition.
     *
     * @param serverId Server ID
     * @param toolId   Tool ID
     * @param request  Updated tool payload
     * @return Mono emitting updated tool DTO
     */
    public Mono<McpToolDto> updateManualTool(String serverId, String toolId, CreateManualToolRequest request) {
        return mcpServerRepository.findById(serverId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP Server not found")))
                .flatMap(server -> mcpToolRepository.findById(toolId)
                        .filter(tool -> tool.getMcpServerId().equals(serverId))
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Tool not found")))
                        .flatMap(tool -> {
                            String cleanName = request.getToolName().trim().replaceAll("[^a-zA-Z0-9_]", "_");
                            String namespaced = computeNamespacedName(server.getName(), cleanName);

                            tool.setToolName(cleanName);
                            tool.setNamespacedName(namespaced);
                            tool.setDescription(request.getDescription());
                            tool.setInputSchema(request.getInputSchema());
                            tool.setNewEntity(false);

                            boolean isManual = McpConstants.CAPABILITY_NON_MCP_REST.equals(server.getCapabilityStatus());
                            return mcpToolRepository.save(tool)
                                    .map(saved -> toToolDto(saved, server.getName(), true, isManual));
                        }));
    }

    /**
     * Deletes a manual tool definition and its group access rules.
     *
     * @param serverId Server ID
     * @param toolId   Tool ID
     * @return Mono completing upon deletion
     */
    public Mono<Void> deleteManualTool(String serverId, String toolId) {
        return mcpToolRepository.findById(toolId)
                .filter(tool -> tool.getMcpServerId().equals(serverId))
                .flatMap(tool -> groupMcpToolAccessRepository.deleteByMcpToolId(toolId)
                        .then(mcpToolRepository.delete(tool)));
    }

    /**
     * Executes a test invocation for a manual REST tool against the target MCP server.
     *
     * @param serverId Server ID
     * @param request  Test request payload containing toolName, description, inputSchema
     * @return Mono emitting Map of test results (status, statusCode, targetUrl, method, durationMs, responseBody)
     */
    public Mono<Map<String, Object>> testManualTool(String serverId, CreateManualToolRequest request) {
        long startTime = System.currentTimeMillis();

        return mcpServerRepository.findById(serverId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP Server not found")))
                .flatMap(server -> {
                    String targetUrl = server.getEndpointUrl();
                    HttpMethod httpMethod = HttpMethod.POST;
                    Map<String, String> customHeaders = new HashMap<>();
                    Object postBody = null;

                    if (request.getInputSchema() != null && !request.getInputSchema().isBlank()) {
                        try {
                            JsonNode schemaNode = objectMapper.readTree(request.getInputSchema());
                            JsonNode properties = schemaNode.get("properties");
                            if (properties != null && properties.isObject()) {
                                if (properties.has("method") && properties.get("method").has("default")) {
                                    String methodStr = properties.get("method").get("default").asText("POST");
                                    try {
                                        httpMethod = HttpMethod.valueOf(methodStr.toUpperCase());
                                    } catch (Exception ignored) {}
                                }

                                if (properties.has("path") && properties.get("path").has("default")) {
                                    String subPath = properties.get("path").get("default").asText("");
                                    if (subPath != null && subPath.startsWith("/")) {
                                        targetUrl = targetUrl.replaceAll("/+$", "") + subPath;
                                    }
                                }

                                if (properties.has("headers") && properties.get("headers").has("default")) {
                                    JsonNode headersNode = properties.get("headers").get("default");
                                    if (headersNode != null && headersNode.isObject()) {
                                        headersNode.fieldNames().forEachRemaining(key -> {
                                            customHeaders.put(key, headersNode.get(key).asText());
                                        });
                                    }
                                }

                                if (properties.has("payload")) {
                                    JsonNode payloadNode = properties.get("payload");
                                    if (payloadNode.has("default")) {
                                        try {
                                            postBody = objectMapper.treeToValue(payloadNode.get("default"), Object.class);
                                        } catch (Exception ignored) {}
                                    }

                                    if (postBody == null && payloadNode.has("properties")) {
                                        Map<String, Object> sampleParams = new HashMap<>();
                                        JsonNode payloadProps = payloadNode.get("properties");
                                        payloadProps.fieldNames().forEachRemaining(key -> {
                                            JsonNode field = payloadProps.get(key);
                                            if (field.has("default")) {
                                                try {
                                                    sampleParams.put(key, objectMapper.treeToValue(field.get("default"), Object.class));
                                                } catch (Exception e) {
                                                    sampleParams.put(key, field.get("default").asText());
                                                }
                                            } else if (field.has("type") && "array".equalsIgnoreCase(field.get("type").asText())) {
                                                sampleParams.put(key, List.of("sample_value"));
                                            } else {
                                                sampleParams.put(key, "sample_value");
                                            }
                                        });
                                        postBody = sampleParams;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("[Manual Tool Test] Failed to parse inputSchema for testing: {}", e.getMessage());
                        }
                    }

                    WebClient.RequestBodySpec spec = aiWebClient.method(httpMethod)
                            .uri(targetUrl)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Accept", "application/json, text/event-stream, text/plain, */*");

                    applyAuthHeaders(spec, server);
                    if (!customHeaders.isEmpty()) {
                        spec.headers(h -> customHeaders.forEach(h::set));
                    }

                    WebClient.RequestHeadersSpec<?> headersSpec = (postBody != null && httpMethod != HttpMethod.GET) ? spec.bodyValue(postBody) : spec;

                    String finalTargetUrl = targetUrl;
                    String finalMethod = httpMethod.name();

                    return headersSpec.exchangeToMono(response -> {
                        long duration = System.currentTimeMillis() - startTime;
                        int statusCode = response.statusCode().value();

                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(bodyStr -> {
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("status", statusCode >= 200 && statusCode < 300 ? "SUCCESS" : "ERROR");
                                    result.put("statusCode", statusCode);
                                    result.put("targetUrl", finalTargetUrl);
                                    result.put("method", finalMethod);
                                    result.put("durationMs", duration);
                                    result.put("responseBody", bodyStr);
                                    return result;
                                });
                    }).onErrorResume(e -> {
                        long duration = System.currentTimeMillis() - startTime;
                        Map<String, Object> errorResult = new HashMap<>();
                        errorResult.put("status", "FAILED");
                        errorResult.put("statusCode", 500);
                        errorResult.put("targetUrl", finalTargetUrl);
                        errorResult.put("method", finalMethod);
                        errorResult.put("durationMs", duration);
                        errorResult.put("error", extractExceptionDetails(e, finalTargetUrl));
                        return Mono.just(errorResult);
                    });
                });
    }

    private String extractExceptionDetails(Throwable e, String targetUrl) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String msg = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();

        if (root instanceof UnknownHostException || msg.contains("UnknownHostException") || msg.contains("name resolution")) {
            String host = "target host";
            try {
                URI uri = URI.create(targetUrl);
                if (uri.getHost() != null) {
                    host = uri.getHost();
                }
            } catch (Exception ignored) {}

            String advice = host.equals("host.docker.internal")
                    ? " (If running in Linux Docker, ensure 'extra_hosts: [\"host.docker.internal:host-gateway\"]' is set in docker-compose.yml, or use an accessible IP/domain)"
                    : "";

            return String.format("DNS Resolution Failed: Unable to resolve hostname '%s'%s.", host, advice);
        }

        if (msg.contains("Connection refused") || msg.contains("finishConnect")) {
            return String.format("Connection Refused: Unable to connect to '%s'. Verify that target service is running.", targetUrl);
        }

        if (msg.contains("Timeout") || msg.contains("ReadTimeoutException")) {
            return String.format("Connection Timeout: Target server '%s' did not respond in time.", targetUrl);
        }

        return msg;
    }

    /**
     * Imports an OpenAPI 3.0 specification text into namespaced REST tools.
     *
     * @param serverId Server ID
     * @param request  OpenAPI import payload
     * @return Mono emitting list of created tool DTOs
     */
    public Mono<List<McpToolDto>> importOpenApiSpec(String serverId, OpenApiImportRequest request) {
        return mcpServerRepository.findById(serverId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP Server not found")))
                .flatMap(server -> {
                    server.setCapabilityStatus(McpConstants.CAPABILITY_NON_MCP_REST);
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

    /**
     * Retrieves discovered MCP resources for a server.
     *
     * @param serverId Server ID
     * @return Flux of MCP resources
     */
    public Flux<McpResource> getDiscoveredResources(String serverId) {
        return mcpResourceRepository.findByMcpServerId(serverId);
    }

    /**
     * Retrieves discovered MCP prompts for a server.
     *
     * @param serverId Server ID
     * @return Flux of MCP prompts
     */
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

    private String computeNamespacedName(String serverName, String toolName) {
        String cleanServer = serverName.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        return cleanServer + "__" + toolName;
    }

    private McpToolDto toToolDto(McpTool tool, String serverName, boolean isEnabled) {
        return toToolDto(tool, serverName, isEnabled, false);
    }

    private McpToolDto toToolDto(McpTool tool, String serverName, boolean isEnabled, boolean isManual) {
        return McpToolDto.builder()
                .id(tool.getId())
                .mcpServerId(tool.getMcpServerId())
                .mcpServerName(serverName)
                .toolName(tool.getToolName())
                .namespacedName(tool.getNamespacedName())
                .description(tool.getDescription())
                .inputSchema(tool.getInputSchema())
                .isAvailable(tool.isAvailable())
                .isManual(isManual)
                .failedSyncCount(tool.getFailedSyncCount())
                .isEnabledForGroup(isEnabled)
                .lastSyncedAt(tool.getLastSyncedAt())
                .createdAt(tool.getCreatedAt())
                .build();
    }
}

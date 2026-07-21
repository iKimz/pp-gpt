package com.ppgpt.gateway.service;

import com.ppgpt.gateway.domain.McpServer;
import com.ppgpt.gateway.dto.CreateMcpServerRequest;
import com.ppgpt.gateway.dto.McpServerDto;
import com.ppgpt.gateway.repository.McpServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerService {

    private static final Pattern AUTH_URI_PATTERN = Pattern.compile("authorization_uri=[\"']([^\"']+)[\"']");
    private static final Pattern RESOURCE_META_PATTERN = Pattern.compile("resource_metadata=[\"']([^\"']+)[\"']");

    private final McpServerRepository mcpServerRepository;
    private final CryptoService cryptoService;
    private final WebClient aiWebClient;

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
                            .contentType(MediaType.APPLICATION_JSON);

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

                                return response.bodyToMono(Map.class)
                                        .map(body -> Map.<String, Object>of(
                                                "status", "CONNECTED",
                                                "response", body
                                        ));
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
                .createdAt(server.getCreatedAt())
                .build();
    }
}

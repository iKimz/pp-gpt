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
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerService {

    private final McpServerRepository mcpServerRepository;
    private final CryptoService cryptoService;
    private final WebClient aiWebClient;

    public Flux<McpServerDto> getAllMcpServers() {
        return mcpServerRepository.findAll()
                .map(this::toDto);
    }

    public Mono<McpServerDto> createMcpServer(CreateMcpServerRequest request) {
        String id = UUID.randomUUID().toString();
        String encryptedKey = (request.getApiKey() != null && !request.getApiKey().isBlank())
                ? cryptoService.encrypt(request.getApiKey().trim())
                : null;

        McpServer server = McpServer.builder()
                .id(id)
                .name(request.getName().trim())
                .endpointUrl(request.getEndpointUrl().trim())
                .apiKeyEncrypted(encryptedKey)
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
                    existing.setDescription(request.getDescription());
                    if (request.getIsActive() != null) {
                        existing.setIsActive(request.getIsActive());
                    }
                    if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
                        existing.setApiKeyEncrypted(cryptoService.encrypt(request.getApiKey().trim()));
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

                    if (server.getApiKeyEncrypted() != null && !server.getApiKeyEncrypted().isBlank()) {
                        String rawKey = cryptoService.decrypt(server.getApiKeyEncrypted());
                        spec.header("Authorization", "Bearer " + rawKey);
                    }

                    Map<String, Object> jsonRpcBody = Map.of(
                            "jsonrpc", "2.0",
                            "method", "tools/list",
                            "id", 1
                    );

                    return spec.bodyValue(jsonRpcBody)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .timeout(Duration.ofSeconds(5))
                            .map(resp -> Map.<String, Object>of(
                                    "status", "CONNECTED",
                                    "response", resp
                            ))
                            .onErrorResume(err -> Mono.just(Map.of(
                                    "status", "DISCONNECTED",
                                    "error", err.getMessage()
                            )));
                });
    }

    private McpServerDto toDto(McpServer server) {
        return McpServerDto.builder()
                .id(server.getId())
                .name(server.getName())
                .endpointUrl(server.getEndpointUrl())
                .description(server.getDescription())
                .isActive(server.getIsActive())
                .hasApiKey(server.getApiKeyEncrypted() != null && !server.getApiKeyEncrypted().isBlank())
                .createdAt(server.getCreatedAt())
                .build();
    }
}

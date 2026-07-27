package com.ppgpt.gateway.repository;

import com.ppgpt.gateway.domain.McpTool;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface McpToolRepository extends ReactiveCrudRepository<McpTool, String> {
    Flux<McpTool> findByMcpServerId(String mcpServerId);
    Mono<McpTool> findByMcpServerIdAndToolName(String mcpServerId, String toolName);
    Flux<McpTool> findByIsAvailableTrue();
    Mono<Void> deleteByMcpServerId(String mcpServerId);
}

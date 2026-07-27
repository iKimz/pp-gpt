package com.ppgpt.gateway.repository;

import com.ppgpt.gateway.domain.McpPrompt;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface McpPromptRepository extends ReactiveCrudRepository<McpPrompt, String> {
    Flux<McpPrompt> findByMcpServerId(String mcpServerId);
    Mono<McpPrompt> findByMcpServerIdAndPromptName(String mcpServerId, String promptName);
    Mono<Void> deleteByMcpServerId(String mcpServerId);
}

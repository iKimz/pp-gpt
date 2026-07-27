package com.ppgpt.gateway.repository;

import com.ppgpt.gateway.domain.McpResource;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface McpResourceRepository extends ReactiveCrudRepository<McpResource, String> {
    Flux<McpResource> findByMcpServerId(String mcpServerId);
    Mono<McpResource> findByMcpServerIdAndUri(String mcpServerId, String uri);
    Mono<Void> deleteByMcpServerId(String mcpServerId);
}

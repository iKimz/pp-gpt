package com.ppgpt.gateway.repository;

import com.ppgpt.gateway.domain.McpServer;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface McpServerRepository extends ReactiveCrudRepository<McpServer, String> {
    Flux<McpServer> findByIsActiveTrue();
}

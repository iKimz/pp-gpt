package com.ppgpt.gateway.repository;

import com.ppgpt.gateway.domain.GroupMcpToolAccess;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface GroupMcpToolAccessRepository extends ReactiveCrudRepository<GroupMcpToolAccess, String> {
    Flux<GroupMcpToolAccess> findByGroupId(String groupId);
    Mono<GroupMcpToolAccess> findByGroupIdAndMcpToolId(String groupId, String mcpToolId);
    Mono<Void> deleteByGroupId(String groupId);
    Mono<Void> deleteByMcpToolId(String mcpToolId);
}

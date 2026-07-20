package com.ppgpt.gateway.repository;

import com.ppgpt.gateway.domain.GroupModelAccess;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface GroupModelAccessRepository extends R2dbcRepository<GroupModelAccess, String> {

    Flux<GroupModelAccess> findByGroupId(String groupId);

    Mono<Boolean> existsByGroupIdAndModelId(String groupId, String modelId);

    @Modifying
    @Query("DELETE FROM group_model_access WHERE group_id = :groupId")
    Mono<Void> deleteByGroupId(String groupId);
}

package com.ppgpt.gateway.repository;

import com.ppgpt.gateway.domain.UserGroup;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface UserGroupRepository extends R2dbcRepository<UserGroup, String> {

    Mono<UserGroup> findByGroupName(String groupName);
}

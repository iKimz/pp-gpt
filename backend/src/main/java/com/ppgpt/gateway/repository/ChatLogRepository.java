package com.ppgpt.gateway.repository;

import com.ppgpt.gateway.domain.ChatLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface ChatLogRepository extends R2dbcRepository<ChatLog, String> {

    Flux<ChatLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}

package com.ppgpt.gateway.repository;

import com.ppgpt.gateway.domain.TokenUsage;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface TokenUsageRepository extends R2dbcRepository<TokenUsage, String> {

    Mono<TokenUsage> findByUserIdAndUsageDate(String userId, LocalDate usageDate);

    /**
     * Atomic upsert: insert or add to existing credits_used for today.
     * Uses MariaDB ON DUPLICATE KEY UPDATE to avoid race conditions.
     */
    @Modifying
    @Query("""
        INSERT INTO token_usage (id, user_id, usage_date, credits_used)
        VALUES (:id, :userId, :usageDate, :credits)
        ON DUPLICATE KEY UPDATE credits_used = credits_used + :credits
        """)
    Mono<Void> upsertCredits(String id, String userId, LocalDate usageDate, BigDecimal credits);
}

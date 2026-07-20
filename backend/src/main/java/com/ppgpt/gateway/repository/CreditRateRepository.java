package com.ppgpt.gateway.repository;

import com.ppgpt.gateway.domain.CreditRate;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface CreditRateRepository extends R2dbcRepository<CreditRate, String> {

    Mono<CreditRate> findByModelId(String modelId);
}

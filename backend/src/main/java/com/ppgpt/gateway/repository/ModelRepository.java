package com.ppgpt.gateway.repository;

import com.ppgpt.gateway.domain.Model;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface ModelRepository extends R2dbcRepository<Model, String> {

    Flux<Model> findByIsActiveTrue();
}

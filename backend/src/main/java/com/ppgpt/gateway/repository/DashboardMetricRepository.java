package com.ppgpt.gateway.repository;

import com.ppgpt.gateway.domain.DashboardMetric;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface DashboardMetricRepository extends ReactiveCrudRepository<DashboardMetric, String> {

    Mono<DashboardMetric> findByGroupIdAndModelIdAndUsageDate(String groupId, String modelId, LocalDate usageDate);

    Flux<DashboardMetric> findByUsageDateBetween(LocalDate startDate, LocalDate endDate);

    Flux<DashboardMetric> findByUsageDateGreaterThanEqual(LocalDate startDate);

    Flux<DashboardMetric> findByUsageDateLessThanEqual(LocalDate endDate);
}

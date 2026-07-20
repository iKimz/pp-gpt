package com.ppgpt.gateway.event;

import com.ppgpt.gateway.domain.DashboardMetric;
import com.ppgpt.gateway.repository.DashboardMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardMetricEventListener {

    private final DashboardMetricRepository repository;
    private final R2dbcEntityTemplate       entityTemplate;

    @Async
    @EventListener
    public void onTokenUsageRecorded(TokenUsageRecordedEvent event) {
        if (event.groupId() == null || event.modelId() == null) {
            return;
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        repository.findByGroupIdAndModelIdAndUsageDate(event.groupId(), event.modelId(), today)
                .flatMap(existing -> {
                    existing.setTotalInputTokens(existing.getTotalInputTokens() + event.inputTokens());
                    existing.setTotalOutputTokens(existing.getTotalOutputTokens() + event.outputTokens());
                    return repository.save(existing);
                })
                .switchIfEmpty(
                        entityTemplate.insert(DashboardMetric.builder()
                                .id(UUID.randomUUID().toString())
                                .groupId(event.groupId())
                                .modelId(event.modelId())
                                .usageDate(today)
                                .totalInputTokens(event.inputTokens())
                                .totalOutputTokens(event.outputTokens())
                                .build())
                )
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        metric -> log.debug("Dashboard metric updated for group={} model={}", event.groupId(), event.modelId()),
                        err -> log.error("Failed to update dashboard metric: {}", err.getMessage())
                );
    }
}

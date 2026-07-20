package com.ppgpt.gateway.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("dashboard_metrics")
public class DashboardMetric {

    @Id
    private String id;

    @Column("group_id")
    private String groupId;

    @Column("model_id")
    private String modelId;

    @Column("usage_date")
    private LocalDate usageDate;

    @Column("total_input_tokens")
    private long totalInputTokens;

    @Column("total_output_tokens")
    private long totalOutputTokens;
}

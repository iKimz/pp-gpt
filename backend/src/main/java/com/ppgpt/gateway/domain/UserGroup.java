package com.ppgpt.gateway.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_groups")
public class UserGroup {

    @Id
    private String id;

    @Column("group_name")
    private String groupName;

    @Column("max_daily_credits")
    private BigDecimal maxDailyCredits;

    @Column("guardrail_model_id")
    private String guardrailModelId;

    @Column("created_at")
    private LocalDateTime createdAt;
}

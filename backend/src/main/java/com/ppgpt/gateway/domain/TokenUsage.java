package com.ppgpt.gateway.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("token_usage")
public class TokenUsage {

    @Id
    private String id;

    @Column("user_id")
    private String userId;

    @Column("usage_date")
    private LocalDate usageDate;

    @Column("credits_used")
    private BigDecimal creditsUsed;
}

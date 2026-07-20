package com.ppgpt.gateway.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("credit_rates")
public class CreditRate {

    @Id
    private String id;

    @Column("model_id")
    private String modelId;

    /** Credits consumed per input token (multiply by input token count). */
    @Column("input_multiplier")
    private BigDecimal inputMultiplier;

    /** Credits consumed per output token (multiply by output token count). */
    @Column("output_multiplier")
    private BigDecimal outputMultiplier;
}

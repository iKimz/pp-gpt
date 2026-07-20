package com.ppgpt.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CreditRateDto {
    private String id;
    private String modelId;
    private String modelName;      // enriched for display
    private BigDecimal inputMultiplier;
    private BigDecimal outputMultiplier;
}

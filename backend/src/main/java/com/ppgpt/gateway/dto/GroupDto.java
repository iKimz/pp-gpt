package com.ppgpt.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class GroupDto {
    private String id;
    private String groupName;
    private BigDecimal maxDailyCredits;
    /** List of model IDs this group can access. */
    private List<String> allowedModelIds;
    private String guardrailModelId;
}

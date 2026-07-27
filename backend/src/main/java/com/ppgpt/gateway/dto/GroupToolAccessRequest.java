package com.ppgpt.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupToolAccessRequest {
    private String mcpToolId;

    @JsonProperty("isEnabled")
    private boolean isEnabled;

    @JsonProperty("enabled")
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }
}

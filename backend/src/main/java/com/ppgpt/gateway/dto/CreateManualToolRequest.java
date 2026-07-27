package com.ppgpt.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateManualToolRequest {

    @NotBlank(message = "Tool name is required")
    private String toolName;

    private String description;

    private String inputSchema;
}

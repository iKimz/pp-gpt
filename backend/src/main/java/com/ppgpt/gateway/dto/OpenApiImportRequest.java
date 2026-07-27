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
public class OpenApiImportRequest {

    @NotBlank(message = "OpenAPI spec content is required")
    private String openApiSpec;
}

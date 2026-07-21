package com.ppgpt.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerDto {
    private String id;
    private String name;
    private String endpointUrl;
    private String description;
    private Boolean isActive;
    private Boolean hasApiKey;
    private LocalDateTime createdAt;
}

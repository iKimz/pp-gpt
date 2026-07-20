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
public class AuditLogDto {
    private String id;
    private String userId;
    private String username;
    private String modelId;
    private String modelDisplayName;
    private String sessionId;
    private String prompt;
    private String response;
    private LocalDateTime createdAt;
}

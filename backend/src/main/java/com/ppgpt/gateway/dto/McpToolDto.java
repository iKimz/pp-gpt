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
public class McpToolDto {
    private String id;
    private String mcpServerId;
    private String mcpServerName;
    private String toolName;
    private String namespacedName;
    private String description;
    private String inputSchema;
    private boolean isAvailable;
    private int failedSyncCount;
    private boolean isEnabledForGroup;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createdAt;
}

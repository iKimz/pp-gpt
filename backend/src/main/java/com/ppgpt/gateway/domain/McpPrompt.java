package com.ppgpt.gateway.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("mcp_prompts")
public class McpPrompt implements Persistable<String> {

    @Id
    private String id;

    @Column("mcp_server_id")
    private String mcpServerId;

    @Column("prompt_name")
    private String promptName;

    @Column("description")
    private String description;

    @Column("arguments_json")
    private String argumentsJson;

    @Column("is_available")
    @Builder.Default
    private boolean isAvailable = true;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Transient
    @Builder.Default
    private boolean isNewEntity = false;

    @Override
    public boolean isNew() {
        return isNewEntity || id == null;
    }
}

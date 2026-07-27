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
@Table("mcp_resources")
public class McpResource implements Persistable<String> {

    @Id
    private String id;

    @Column("mcp_server_id")
    private String mcpServerId;

    @Column("uri")
    private String uri;

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("mime_type")
    private String mimeType;

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

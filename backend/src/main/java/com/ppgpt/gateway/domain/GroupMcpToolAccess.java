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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("group_mcp_tool_access")
public class GroupMcpToolAccess implements Persistable<String> {

    @Id
    private String id;

    @Column("group_id")
    private String groupId;

    @Column("mcp_tool_id")
    private String mcpToolId;

    @Column("is_enabled")
    @Builder.Default
    private boolean isEnabled = true;

    @Transient
    @Builder.Default
    private boolean isNewEntity = false;

    @Override
    public boolean isNew() {
        return isNewEntity || id == null;
    }
}

package com.ppgpt.gateway.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Maps which AI models a user group can access.
 * Uses a surrogate UUID PK for R2DBC compatibility;
 * business uniqueness (group_id, model_id) enforced by DB UNIQUE constraint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("group_model_access")
public class GroupModelAccess {

    @Id
    private String id;

    @Column("group_id")
    private String groupId;

    @Column("model_id")
    private String modelId;
}

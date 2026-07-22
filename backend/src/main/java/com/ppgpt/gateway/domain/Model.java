package com.ppgpt.gateway.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("models")
public class Model {

    @Id
    private String id;

    @Column("name")
    private String name;

    @Column("provider")
    private String provider;

    @Column("model_name")
    private String modelName;

    @Column("endpoint_url")
    private String endpointUrl;

    /**
     * AES-256-GCM encrypted JSON string. Format depends on provider:
     * <ul>
     *   <li>OPENAI:      {@code {"apiKey": "sk-..."}}</li>
     *   <li>AZURE:       {@code {"apiKey": "...", "apiVersion": "2024-02-01"}}</li>
     *   <li>AWS_BEDROCK: {@code {"accessKeyId": "...", "secretAccessKey": "...", "region": "us-east-1"}}</li>
     * </ul>
     */
    @Column("credentials_encrypted")
    private String credentialsEncrypted;

    @Column("is_active")
    private boolean isActive;

    /** Maximum time in milliseconds to wait for a provider response before timing out. */
    @Column("timeout_ms")
    @Builder.Default
    private int timeoutMs = 30000;

    /** Sampling temperature (0.0 = deterministic, 2.0 = max randomness). */
    @Column("temperature")
    @Builder.Default
    private double temperature = 0.7;

    /** Optional system prompt injected as the first message in the conversation. */
    @Column("system_prompt")
    private String systemPrompt;

    /** Max number of previous conversation turns to include as context. */
    @Column("max_history_messages")
    @Builder.Default
    private int maxHistoryMessages = 10;

    /** Type of the model: GENERATION or GUARDRAIL */
    @Column("model_type")
    @Builder.Default
    private String modelType = "GENERATION";

    /** Indicates if the model supports image attachments (multimodal) */
    @Column("supports_vision")
    private boolean supportsVision;

    /** Indicates if the model supports tool calling / MCP tools */
    @Column("supports_tools")
    @Builder.Default
    private boolean supportsTools = true;

    @Column("created_at")
    private LocalDateTime createdAt;
}

package com.ppgpt.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelDto {
    private String id;
    private String name;
    private String provider;
    private String modelName;
    private String endpointUrl;
    private String modelType;

    /**
     * Write-only credentials JSON. Never returned in responses.
     * Leave null or blank on PUT to keep the existing credentials.
     *
     * <p>Supported formats:
     * <ul>
     *   <li>OPENAI:      {@code {"apiKey": "sk-..."}}</li>
     *   <li>AZURE:       {@code {"apiKey": "...", "apiVersion": "2024-02-01"}}</li>
     *   <li>AWS_BEDROCK: {@code {"accessKeyId": "...", "secretAccessKey": "...", "region": "us-east-1"}}</li>
     * </ul>
     */
    private String credentials;   // write-only, plaintext JSON (encrypted server-side)

    private boolean isActive;

    /** Maximum wait time in milliseconds before aborting the request. Default: 30000 */
    @Builder.Default
    private int timeoutMs = 30000;

    /** Sampling temperature (0.0 = deterministic, 2.0 = max randomness). Default: 0.7 */
    @Builder.Default
    private double temperature = 0.7;

    /** Optional system prompt defining the AI persona/behavior for this model. */
    private String systemPrompt;

    /** Number of previous chat messages to send for context. Default: 10 */
    @Builder.Default
    private int maxHistoryMessages = 10;
}

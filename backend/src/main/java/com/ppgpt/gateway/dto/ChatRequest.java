package com.ppgpt.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
public class ChatRequest {

    @NotBlank(message = "modelId is required")
    private String modelId;

    @NotBlank(message = "message is required")
    private String message;

    /** Client-side session UUID for grouping messages into conversations. */
    @NotNull
    private String sessionId;

    /**
     * Previous conversation turns for context.
     * Each entry has keys: {@code role} ("user"|"assistant") and {@code content} (String).
     * The backend will slice this list according to the model's {@code max_history_messages} setting.
     */
    private List<Map<String, String>> history = Collections.emptyList();

    /**
     * Optional image attachments as Base64 Data URLs (e.g. "data:image/png;base64,...").
     */
    private List<String> images = Collections.emptyList();
}


package com.ppgpt.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Represents a tool / function definition passed to LLM models.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolDto(
    String type,            // e.g. "function"
    FunctionDef function    // function metadata & parameter JSON schema
) {
    public record FunctionDef(
        String name,
        String description,
        Map<String, Object> parameters
    ) {}
}

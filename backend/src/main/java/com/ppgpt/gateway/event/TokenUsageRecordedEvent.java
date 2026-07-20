package com.ppgpt.gateway.event;

public record TokenUsageRecordedEvent(
    String groupId,
    String modelId,
    long inputTokens,
    long outputTokens
) {}

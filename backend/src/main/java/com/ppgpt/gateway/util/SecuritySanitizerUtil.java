package com.ppgpt.gateway.util;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Security & Input Sanitization Utility for AI Gateway.
 * Provides prompt injection filtering, jailbreak detection, and OSS model reasoning tag stripping.
 */
@Slf4j
public final class SecuritySanitizerUtil {

    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("<think>.*?</think>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern REASONING_TAG_PATTERN = Pattern.compile("<reasoning>.*?</reasoning>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern JAILBREAK_PATTERN = Pattern.compile(
            "(?i)(ignore\\s+all\\s+previous\\s+instructions|system\\s+override|reveal\\s+admin\\s+password|act\\s+as\\s+root|disable\\s+safety\\s+filters)"
    );

    private static final int MAX_DESCRIPTION_LENGTH = 1000;

    private SecuritySanitizerUtil() {
        // Private constructor for utility class
    }

    /**
     * Sanitizes tool descriptions by filtering prompt injection keywords and enforcing max length boundaries.
     *
     * @param description Raw tool description input
     * @return Sanitized description string
     */
    public static String sanitizeToolDescription(String description) {
        if (description == null || description.isBlank()) {
            return "No description provided.";
        }

        String filtered = JAILBREAK_PATTERN.matcher(description).replaceAll("[FILTERED]");
        if (filtered.length() > MAX_DESCRIPTION_LENGTH) {
            filtered = filtered.substring(0, MAX_DESCRIPTION_LENGTH) + "...";
        }
        return filtered;
    }

    /**
     * Strips OSS reasoning tags (<think>...</think> or <reasoning>...</reasoning>) from model streaming output.
     *
     * @param content Raw LLM content chunk
     * @return Cleaned content string
     */
    public static String stripReasoningTags(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String cleaned = THINK_TAG_PATTERN.matcher(content).replaceAll("");
        cleaned = REASONING_TAG_PATTERN.matcher(cleaned).replaceAll("");
        return cleaned;
    }
}

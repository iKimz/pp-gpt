package com.ppgpt.gateway.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TokenizerUtil}.
 * No Spring context needed — TokenizerUtil has no external dependencies.
 */
class TokenizerUtilTest {

    private TokenizerUtil tokenizerUtil;

    @BeforeEach
    void setUp() {
        tokenizerUtil = new TokenizerUtil();
    }

    @Test
    void countTokens_nullText_returnsZero() {
        assertThat(tokenizerUtil.countTokens("gpt-4o", null)).isZero();
    }

    @Test
    void countTokens_blankText_returnsZero() {
        assertThat(tokenizerUtil.countTokens("gpt-4o", "   ")).isZero();
    }

    @Test
    void countTokens_gpt4o_returnsPositiveCount() {
        int tokens = tokenizerUtil.countTokens("gpt-4o", "Hello, World!");
        assertThat(tokens).isGreaterThan(0);
    }

    @Test
    void countTokens_gpt35turbo_returnsPositiveCount() {
        int tokens = tokenizerUtil.countTokens("gpt-3.5-turbo", "Hello, World!");
        assertThat(tokens).isGreaterThan(0);
    }

    @Test
    void countTokens_longerTextHasMoreTokensThanShorterText() {
        int shortCount = tokenizerUtil.countTokens("gpt-4o", "Hi");
        int longCount = tokenizerUtil.countTokens("gpt-4o",
                "This is a much longer sentence with many more words that should produce more tokens.");
        assertThat(longCount).isGreaterThan(shortCount);
    }

    @Test
    void countTokens_unknownModel_usesFallback_returnsPositiveCount() {
        int tokens = tokenizerUtil.countTokens("aws-bedrock-claude-v9-unknown", "Test message here.");
        assertThat(tokens).isGreaterThan(0);
    }

    @Test
    void countTokens_unknownModel_fallbackIsAtLeastOne() {
        int tokens = tokenizerUtil.countTokens("UNKNOWN_MODEL_XYZ", "A");
        assertThat(tokens).isGreaterThanOrEqualTo(1);
    }
}

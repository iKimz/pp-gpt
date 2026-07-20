package com.ppgpt.gateway.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;



/**
 * Wraps JTokkit BPE tokenizer for OpenAI-compatible token counting.
 * Used to count input/output tokens for credit calculation.
 *
 * Falls back to character-based estimation (~4 chars/token) for unknown models.
 */
@Slf4j
@Component
public class TokenizerUtil {

    private final EncodingRegistry registry;

    public TokenizerUtil() {
        this.registry = Encodings.newDefaultEncodingRegistry();
    }

    /**
     * Count tokens for a given text using the model's encoding.
     *
     * @param modelName  the model identifier (e.g., "gpt-4o", "gpt-3.5-turbo")
     * @param text       the text to tokenize
     * @return token count
     */
    public int countTokens(String modelName, String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        try {
            java.util.Optional<Encoding> encoding = registry.getEncodingForModel(modelName);
            if (encoding.isPresent()) {
                return encoding.get().countTokens(text);
            }

            // Attempt to match by model type prefix
            for (ModelType modelType : ModelType.values()) {
                if (modelName.toLowerCase().contains(modelType.getName().toLowerCase())) {
                    Encoding encByType = registry.getEncodingForModel(modelType);
                    return encByType.countTokens(text);
                }
            }
        } catch (Exception e) {
            log.warn("JTokkit failed for model '{}', using character-based fallback: {}", modelName, e.getMessage());
        }

        // Fallback: ~4 characters per token (rough OpenAI estimate)
        return Math.max(1, text.length() / 4);
    }
}


package com.ppgpt.gateway.adapter;

import com.ppgpt.gateway.domain.Model;
import com.ppgpt.gateway.dto.ChatRequest;
import reactor.core.publisher.Flux;

/**
 * Strategy interface for AI provider adapters.
 *
 * <p>Each adapter is responsible for:
 * <ol>
 *   <li>Parsing the decrypted credentials JSON for its provider.</li>
 *   <li>Building and signing the provider-specific HTTP request.</li>
 *   <li>Streaming the raw response back as a {@code Flux<String>}
 *       where each element is a <em>content fragment</em> (not a raw SSE line).</li>
 * </ol>
 *
 * <p>Token counting, credit deduction, and chat-log persistence are handled
 * by {@link com.ppgpt.gateway.service.ChatService} and are <strong>NOT</strong>
 * the responsibility of any adapter.
 */
public interface AiProviderAdapter {

    /**
     * Returns the provider key this adapter handles (case-insensitive match).
     * Examples: {@code "OPENAI"}, {@code "AZURE"}, {@code "AWS_BEDROCK"}.
     */
    String providerKey();

    /**
     * Stream a chat completion from the provider.
     *
     * @param request              the user's chat request
     * @param model                the fully populated {@link Model} entity
     * @param decryptedCredentials plaintext JSON credentials string
     * @return a {@link Flux} of content-fragment strings to be accumulated
     *         by the core service for token counting
     */
    Flux<String> streamChat(ChatRequest request, Model model, String decryptedCredentials);
}

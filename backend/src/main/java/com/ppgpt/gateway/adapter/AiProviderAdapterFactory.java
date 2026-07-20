package com.ppgpt.gateway.adapter;

import com.ppgpt.gateway.domain.Model;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Factory that selects the correct {@link AiProviderAdapter} based on
 * the model's {@code provider} field (case-insensitive).
 *
 * <p>
 * The factory is injected with all {@link AiProviderAdapter} beans discovered
 * by Spring, keyed by their {@link AiProviderAdapter#providerKey()}. This means
 * adding a new adapter is as simple as creating a new {@code @Component} that
 * implements the interface — no factory changes required.
 */
@Slf4j
@Component
public class AiProviderAdapterFactory {

    private final Map<String, AiProviderAdapter> registry;

    public AiProviderAdapterFactory(List<AiProviderAdapter> adapters) {
        registry = new java.util.HashMap<>();
        adapters.forEach(a -> {
            registry.put(a.providerKey().toUpperCase(), a);
            log.info("Registered AI provider adapter: {}", a.providerKey());
        });
    }

    /**
     * Resolve the adapter for the given provider string.
     *
     * @param provider the {@code provider} field from the {@link Model} entity
     * @return the matching adapter
     * @throws ResponseStatusException (503) if no adapter is registered
     */
    public AiProviderAdapter resolve(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Model has no provider configured");
        }
        AiProviderAdapter adapter = registry.get(provider.toUpperCase());
        if (adapter == null) {
            log.warn("No adapter found for provider '{}'. Falling back to OPENAI adapter.", provider);
            adapter = registry.get("OPENAI");
        }
        if (adapter == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No AI provider adapter available for provider: " + provider);
        }
        return adapter;
    }
}

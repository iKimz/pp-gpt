package com.ppgpt.gateway.adapter;

import com.ppgpt.gateway.domain.Model;
import com.ppgpt.gateway.dto.ChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AiProviderAdapterFactory}.
 */
class AiProviderAdapterFactoryTest {

    private static AiProviderAdapter stubAdapter(String key) {
        return new AiProviderAdapter() {
            @Override public String providerKey() { return key; }
            @Override public Flux<String> streamChat(ChatRequest r, Model m, String c) {
                return Flux.empty();
            }
        };
    }

    @Test
    void resolve_knownProvider_returnsCorrectAdapter() {
        AiProviderAdapterFactory factory =
                new AiProviderAdapterFactory(List.of(stubAdapter("OPENAI"), stubAdapter("AZURE")));
        assertThat(factory.resolve("AZURE").providerKey()).isEqualTo("AZURE");
    }

    @Test
    void resolve_isCaseInsensitive() {
        AiProviderAdapterFactory factory =
                new AiProviderAdapterFactory(List.of(stubAdapter("OPENAI"), stubAdapter("AZURE")));
        assertThat(factory.resolve("azure").providerKey()).isEqualTo("AZURE");
        assertThat(factory.resolve("Azure").providerKey()).isEqualTo("AZURE");
    }

    @Test
    void resolve_unknownProvider_fallsBackToOpenAi() {
        AiProviderAdapterFactory factory =
                new AiProviderAdapterFactory(List.of(stubAdapter("OPENAI"), stubAdapter("AZURE")));
        assertThat(factory.resolve("UNKNOWN_PROVIDER").providerKey()).isEqualTo("OPENAI");
    }

    @Test
    void resolve_nullProvider_throwsBadRequest() {
        AiProviderAdapterFactory factory =
                new AiProviderAdapterFactory(List.of(stubAdapter("OPENAI")));
        assertThatThrownBy(() -> factory.resolve(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void resolve_blankProvider_throwsBadRequest() {
        AiProviderAdapterFactory factory =
                new AiProviderAdapterFactory(List.of(stubAdapter("OPENAI")));
        assertThatThrownBy(() -> factory.resolve("  "))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void resolve_unknownProvider_noFallback_throwsServiceUnavailable() {
        AiProviderAdapterFactory factory =
                new AiProviderAdapterFactory(List.of(stubAdapter("AZURE")));
        assertThatThrownBy(() -> factory.resolve("UNKNOWN"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void constructor_registersAllAdapters() {
        AiProviderAdapterFactory factory = new AiProviderAdapterFactory(
                List.of(stubAdapter("OPENAI"), stubAdapter("AZURE"), stubAdapter("AWS_BEDROCK")));
        assertThat(factory.resolve("OPENAI").providerKey()).isEqualTo("OPENAI");
        assertThat(factory.resolve("AZURE").providerKey()).isEqualTo("AZURE");
        assertThat(factory.resolve("AWS_BEDROCK").providerKey()).isEqualTo("AWS_BEDROCK");
    }
}

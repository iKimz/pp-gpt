package com.ppgpt.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux HTTP Server Codec configuration.
 * Increases in-memory buffer limit (32MB) for incoming requests containing
 * Base64-encoded image payloads (multimodal vision chat).
 */
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize(32 * 1024 * 1024);
    }
}

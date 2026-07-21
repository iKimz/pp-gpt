package com.ppgpt.gateway.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.transport.ProxyProvider;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient configuration for proxying AI provider requests.
 * Supports outbound HTTP/HTTPS Proxy (Reactor Netty ProxyProvider).
 * Large buffer size (32MB) to handle long SSE streams & Base64 images.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient aiWebClient() {
        ConnectionProvider provider = ConnectionProvider.builder("ai-provider")
                .maxConnections(200)
                .maxIdleTime(Duration.ofSeconds(60))
                .maxLifeTime(Duration.ofMinutes(10))
                .pendingAcquireTimeout(Duration.ofSeconds(15))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(120, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        // Configure Outbound HTTP/HTTPS Proxy for Reactor Netty
        String proxyHost = System.getProperty("https.proxyHost", System.getProperty("http.proxyHost"));
        if (proxyHost == null || proxyHost.isBlank()) {
            String envProxy = System.getenv("HTTPS_PROXY");
            if (envProxy == null || envProxy.isBlank()) envProxy = System.getenv("HTTP_PROXY");
            if (envProxy != null && !envProxy.isBlank()) {
                try {
                    URI uri = new URI(envProxy.startsWith("http") ? envProxy : "http://" + envProxy);
                    proxyHost = uri.getHost();
                } catch (Exception ignored) {}
            }
        }

        String proxyPortStr = System.getProperty("https.proxyPort", System.getProperty("http.proxyPort", "3128"));
        int proxyPort = 3128;
        try {
            proxyPort = Integer.parseInt(proxyPortStr);
        } catch (Exception ignored) {}

        String defaultNonProxy = "localhost|127.0.0.1|mariadb|redis|vault|hiroshi-nlu|chatservice|chatservice2|host.docker.internal";
        String nonProxyHosts = System.getProperty("http.nonProxyHosts", defaultNonProxy);

        if (proxyHost != null && !proxyHost.isBlank()) {
            final String finalHost = proxyHost;
            final int finalPort = proxyPort;
            final String finalNonProxy = nonProxyHosts;

            httpClient = httpClient.proxy(proxy -> proxy.type(ProxyProvider.Proxy.HTTP)
                    .host(finalHost)
                    .port(finalPort)
                    .nonProxyHosts(finalNonProxy));
        }

        // 32MB buffer for SSE streaming and multimodal image payloads
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .build();
    }
}

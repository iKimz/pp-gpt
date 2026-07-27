package com.ppgpt.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppgpt.gateway.domain.McpServer;
import com.ppgpt.gateway.repository.GroupMcpToolAccessRepository;
import com.ppgpt.gateway.repository.McpPromptRepository;
import com.ppgpt.gateway.repository.McpResourceRepository;
import com.ppgpt.gateway.repository.McpServerRepository;
import com.ppgpt.gateway.repository.McpToolRepository;
import com.ppgpt.gateway.util.McpConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpServerServiceTest {

    @Mock
    private McpServerRepository mcpServerRepository;
    @Mock
    private McpToolRepository mcpToolRepository;
    @Mock
    private McpResourceRepository mcpResourceRepository;
    @Mock
    private McpPromptRepository mcpPromptRepository;
    @Mock
    private GroupMcpToolAccessRepository groupMcpToolAccessRepository;
    @Mock
    private CryptoService cryptoService;
    @Mock
    private WebClient aiWebClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private WebClient.RequestBodySpec requestBodySpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Test
    @DisplayName("executeTool - Non-MCP REST forwards Custom Headers, Method, Subpath, and Payload")
    void executeTool_NonMcpRest_ForwardsCustomHeaders() {
        McpServerService service = new McpServerService(
                mcpServerRepository,
                mcpToolRepository,
                mcpResourceRepository,
                mcpPromptRepository,
                groupMcpToolAccessRepository,
                cryptoService,
                aiWebClient,
                new ObjectMapper()
        );

        McpServer server = McpServer.builder()
                .id("srv-1")
                .name("Finance Server")
                .endpointUrl("http://localhost:8080")
                .capabilityStatus(McpConstants.CAPABILITY_NON_MCP_REST)
                .authType("NONE")
                .isActive(true)
                .build();

        when(mcpServerRepository.findByIsActiveTrue()).thenReturn(Flux.just(server));

        when(aiWebClient.method(HttpMethod.POST)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("http://localhost:8080/api/v1/payments")).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(String.class)).thenReturn(Flux.just("{\"status\":\"SUCCESS\",\"transaction_id\":\"TX-9988\"}"));

        Map<String, Object> arguments = Map.of(
                "method", "POST",
                "path", "/api/v1/payments",
                "headers", Map.of(
                        "X-Tenant-Id", "tenant_001",
                        "X-Custom-Header", "hello_world"
                ),
                "amount", 500
        );

        Mono<String> resultMono = service.executeTool("finance_server__process_payment", arguments);

        StepVerifier.create(resultMono)
                .expectNextMatches(resp -> resp.contains("TX-9988"))
                .verifyComplete();

        verify(requestBodySpec).header("X-Tenant-Id", "tenant_001");
        verify(requestBodySpec).header("X-Custom-Header", "hello_world");
    }
}

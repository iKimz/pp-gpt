package com.ppgpt.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppgpt.gateway.domain.McpServer;
import com.ppgpt.gateway.domain.McpTool;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.function.Consumer;

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

        McpTool tool = McpTool.builder()
                .id("tool-1")
                .mcpServerId("srv-1")
                .toolName("process_payment")
                .namespacedName("finance_server__process_payment")
                .isAvailable(true)
                .inputSchema("{\"type\":\"object\",\"properties\":{\"method\":{\"type\":\"string\",\"default\":\"POST\"},\"path\":{\"type\":\"string\",\"default\":\"/api/v1/payments\"},\"headers\":{\"type\":\"object\",\"default\":{\"X-Tenant-Id\":\"tenant_001\"}}}}")
                .build();

        when(mcpToolRepository.findByIsAvailableTrue()).thenReturn(Flux.just(tool));
        when(mcpServerRepository.findById("srv-1")).thenReturn(Mono.just(server));

        when(aiWebClient.method(HttpMethod.POST)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("http://localhost:8080/api/v1/payments")).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(String.class)).thenReturn(Flux.just("{\"status\":\"SUCCESS\",\"transaction_id\":\"TX-9988\"}"));

        Map<String, Object> arguments = Map.of(
                "amount", 500
        );

        Mono<String> resultMono = service.executeTool("finance_server__process_payment", arguments);

        StepVerifier.create(resultMono)
                .expectNextMatches(resp -> resp.contains("TX-9988"))
                .verifyComplete();
    }

    @Test
    @DisplayName("executeTool - Standard MCP Server sends JSON-RPC 2.0 payload")
    void executeTool_StandardMcpServer_SendsJsonRpcPayload() {
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
                .id("srv-2")
                .name("Weather MCP")
                .endpointUrl("http://localhost:9090/mcp")
                .capabilityStatus(McpConstants.CAPABILITY_DISCOVERED)
                .authType("NONE")
                .isActive(true)
                .build();

        McpTool tool = McpTool.builder()
                .id("tool-2")
                .mcpServerId("srv-2")
                .toolName("get_weather")
                .namespacedName("weather_mcp__get_weather")
                .isAvailable(true)
                .build();

        when(mcpToolRepository.findByIsAvailableTrue()).thenReturn(Flux.just(tool));
        when(mcpServerRepository.findById("srv-2")).thenReturn(Mono.just(server));

        when(aiWebClient.method(HttpMethod.POST)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("http://localhost:9090/mcp")).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(String.class)).thenReturn(Flux.just("{\"jsonrpc\":\"2.0\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"Temperature: 28C\"}]},\"id\":1}"));

        Map<String, Object> arguments = Map.of("city", "Bangkok");

        Mono<String> resultMono = service.executeTool("weather_mcp__get_weather", arguments);

        StepVerifier.create(resultMono)
                .expectNextMatches(resp -> resp.contains("Temperature: 28C"))
                .verifyComplete();
    }
}

package com.ppgpt.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppgpt.gateway.domain.McpServer;
import com.ppgpt.gateway.domain.McpTool;
import com.ppgpt.gateway.dto.OpenApiImportRequest;
import com.ppgpt.gateway.repository.GroupMcpToolAccessRepository;
import com.ppgpt.gateway.repository.McpPromptRepository;
import com.ppgpt.gateway.repository.McpResourceRepository;
import com.ppgpt.gateway.repository.McpServerRepository;
import com.ppgpt.gateway.repository.McpToolRepository;
import com.ppgpt.gateway.util.McpConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class McpSchemaSanitizerAndNormalizationTest {

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

    private McpServerService mcpServerService;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        WebClient webClient = WebClient.builder().build();
        mcpServerService = new McpServerService(
                mcpServerRepository,
                mcpToolRepository,
                mcpResourceRepository,
                mcpPromptRepository,
                groupMcpToolAccessRepository,
                cryptoService,
                webClient,
                objectMapper
        );
    }

    @Test
    @DisplayName("importOpenApiSpec: Converts OpenAPI v3 JSON endpoints into Legacy REST tools")
    public void testImportOpenApiSpec() {
        String openApiJson = "{\n" +
                "  \"openapi\": \"3.0.0\",\n" +
                "  \"paths\": {\n" +
                "    \"/api/v1/users/{id}\": {\n" +
                "      \"get\": {\n" +
                "        \"summary\": \"Get User Profile\",\n" +
                "        \"operationId\": \"getUserProfile\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        McpServer server = McpServer.builder()
                .id("srv-openapi")
                .name("user_service")
                .endpointUrl("http://localhost:8080")
                .capabilityStatus(McpConstants.CAPABILITY_NON_MCP_REST)
                .build();

        OpenApiImportRequest req = new OpenApiImportRequest();
        req.setOpenApiSpec(openApiJson);

        when(mcpServerRepository.findById("srv-openapi")).thenReturn(Mono.just(server));
        when(mcpServerRepository.save(any(McpServer.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(mcpToolRepository.findByMcpServerIdAndToolName(any(), any())).thenReturn(Mono.empty());
        when(mcpToolRepository.save(any(McpTool.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(mcpServerService.importOpenApiSpec("srv-openapi", req))
                .assertNext(tools -> {
                    assertEquals(1, tools.size());
                    assertEquals("getUserProfile", tools.get(0).getToolName());
                    assertEquals("user_service__getUserProfile", tools.get(0).getNamespacedName());
                })
                .verifyComplete();
    }
}

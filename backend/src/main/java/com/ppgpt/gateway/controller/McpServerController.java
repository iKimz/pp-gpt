package com.ppgpt.gateway.controller;

import com.ppgpt.gateway.domain.McpPrompt;
import com.ppgpt.gateway.domain.McpResource;
import com.ppgpt.gateway.dto.CreateManualToolRequest;
import com.ppgpt.gateway.dto.CreateMcpServerRequest;
import com.ppgpt.gateway.dto.GroupToolAccessRequest;
import com.ppgpt.gateway.dto.McpServerDto;
import com.ppgpt.gateway.dto.McpToolDto;
import com.ppgpt.gateway.dto.OpenApiImportRequest;
import com.ppgpt.gateway.service.McpServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for Model Context Protocol (MCP) server management, tool discovery, manual tool creation, and group tool access control.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class McpServerController {

    private final McpServerService mcpServerService;

    /**
     * Lists all registered MCP servers.
     *
     * @return Flux of McpServerDto
     */
    @GetMapping("/mcp-servers")
    public Flux<McpServerDto> getAllMcpServers() {
        return mcpServerService.getAllMcpServers();
    }

    /**
     * Creates a new MCP server definition.
     *
     * @param request Server creation request payload
     * @return Mono emitting created McpServerDto
     */
    @PostMapping("/mcp-servers")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<McpServerDto> createMcpServer(@Valid @RequestBody CreateMcpServerRequest request) {
        return mcpServerService.createMcpServer(request);
    }

    /**
     * Updates an existing MCP server configuration.
     *
     * @param id      Server ID
     * @param request Update request payload
     * @return Mono emitting updated McpServerDto
     */
    @PutMapping("/mcp-servers/{id}")
    public Mono<McpServerDto> updateMcpServer(@PathVariable String id, @Valid @RequestBody CreateMcpServerRequest request) {
        return mcpServerService.updateMcpServer(id, request);
    }

    /**
     * Deletes an MCP server.
     *
     * @param id Server ID
     * @return Mono completing upon deletion
     */
    @DeleteMapping("/mcp-servers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteMcpServer(@PathVariable String id) {
        return mcpServerService.deleteMcpServer(id);
    }

    /**
     * Tests connection to an MCP server, performing OAuth discovery if unauthorized.
     *
     * @param id Server ID
     * @return Mono emitting connection result map
     */
    @PostMapping("/mcp-servers/{id}/test")
    public Mono<Map<String, Object>> testConnection(@PathVariable String id) {
        return mcpServerService.testConnection(id);
    }

    /**
     * Synchronizes tools for a specific MCP server.
     *
     * @param id Server ID
     * @return Mono emitting list of synchronized tool DTOs
     */
    @PostMapping("/mcp-servers/{id}/sync")
    public Mono<List<McpToolDto>> syncTools(@PathVariable String id) {
        return mcpServerService.syncTools(id);
    }

    /**
     * Synchronizes tools across all active MCP servers.
     *
     * @return Flux of synchronized tool DTOs
     */
    @PostMapping("/mcp-servers/sync-all")
    public Flux<McpToolDto> syncAllTools() {
        return mcpServerService.syncAllTools();
    }

    /**
     * Retrieves discovered tools for a server.
     *
     * @param id Server ID
     * @return Flux of tool DTOs
     */
    @GetMapping("/mcp-servers/{id}/tools")
    public Flux<McpToolDto> getDiscoveredTools(@PathVariable String id) {
        return mcpServerService.getDiscoveredTools(id);
    }

    /**
     * Creates a manual tool definition for Legacy REST endpoints.
     *
     * @param id      Server ID
     * @param request Manual tool request payload
     * @return Mono emitting created tool DTO
     */
    @PostMapping("/mcp-servers/{id}/tools/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<McpToolDto> createManualTool(@PathVariable String id, @Valid @RequestBody CreateManualToolRequest request) {
        return mcpServerService.createManualTool(id, request);
    }

    /**
     * Imports an OpenAPI specification as namespaced tools.
     *
     * @param id      Server ID
     * @param request OpenAPI import request payload
     * @return Mono emitting list of created tool DTOs
     */
    @PostMapping("/mcp-servers/{id}/tools/import-openapi")
    public Mono<List<McpToolDto>> importOpenApiSpec(@PathVariable String id, @Valid @RequestBody OpenApiImportRequest request) {
        return mcpServerService.importOpenApiSpec(id, request);
    }

    /**
     * Deletes a manual tool definition.
     *
     * @param id     Server ID
     * @param toolId Tool ID
     * @return Mono completing upon deletion
     */
    @DeleteMapping("/mcp-servers/{id}/tools/{toolId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteManualTool(@PathVariable String id, @PathVariable String toolId) {
        return mcpServerService.deleteManualTool(id, toolId);
    }

    /**
     * Retrieves discovered MCP resources for a server.
     *
     * @param id Server ID
     * @return Flux of MCP resources
     */
    @GetMapping("/mcp-servers/{id}/resources")
    public Flux<McpResource> getDiscoveredResources(@PathVariable String id) {
        return mcpServerService.getDiscoveredResources(id);
    }

    /**
     * Retrieves discovered MCP prompts for a server.
     *
     * @param id Server ID
     * @return Flux of MCP prompts
     */
    @GetMapping("/mcp-servers/{id}/prompts")
    public Flux<McpPrompt> getDiscoveredPrompts(@PathVariable String id) {
        return mcpServerService.getDiscoveredPrompts(id);
    }

    /**
     * Retrieves group tool access status for a user group.
     *
     * @param groupId Group ID
     * @return Flux of tool DTOs with group authorization status
     */
    @GetMapping("/groups/{groupId}/mcp-tools")
    public Flux<McpToolDto> getGroupToolAccess(@PathVariable String groupId) {
        return mcpServerService.getGroupToolAccess(groupId);
    }

    /**
     * Updates group tool access permissions.
     *
     * @param groupId Group ID
     * @param updates List of permission updates
     * @return Mono completing upon update
     */
    @PutMapping("/groups/{groupId}/mcp-tools")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> updateGroupToolAccess(@PathVariable String groupId, @RequestBody List<GroupToolAccessRequest> updates) {
        return mcpServerService.updateGroupToolAccess(groupId, updates);
    }
}

package com.ppgpt.gateway.controller;

import com.ppgpt.gateway.dto.CreateMcpServerRequest;
import com.ppgpt.gateway.dto.McpServerDto;
import com.ppgpt.gateway.dto.McpToolDto;
import com.ppgpt.gateway.dto.GroupToolAccessRequest;
import com.ppgpt.gateway.service.McpServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class McpServerController {

    private final McpServerService mcpServerService;

    @GetMapping("/mcp-servers")
    public Flux<McpServerDto> getAllMcpServers() {
        return mcpServerService.getAllMcpServers();
    }

    @PostMapping("/mcp-servers")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<McpServerDto> createMcpServer(@Valid @RequestBody CreateMcpServerRequest request) {
        return mcpServerService.createMcpServer(request);
    }

    @PutMapping("/mcp-servers/{id}")
    public Mono<McpServerDto> updateMcpServer(@PathVariable String id, @Valid @RequestBody CreateMcpServerRequest request) {
        return mcpServerService.updateMcpServer(id, request);
    }

    @DeleteMapping("/mcp-servers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteMcpServer(@PathVariable String id) {
        return mcpServerService.deleteMcpServer(id);
    }

    @PostMapping("/mcp-servers/{id}/test")
    public Mono<Map<String, Object>> testConnection(@PathVariable String id) {
        return mcpServerService.testConnection(id);
    }

    @PostMapping("/mcp-servers/{id}/sync")
    public Mono<List<McpToolDto>> syncTools(@PathVariable String id) {
        return mcpServerService.syncTools(id);
    }

    @PostMapping("/mcp-servers/sync-all")
    public Flux<McpToolDto> syncAllTools() {
        return mcpServerService.syncAllTools();
    }

    @GetMapping("/mcp-servers/{id}/tools")
    public Flux<McpToolDto> getDiscoveredTools(@PathVariable String id) {
        return mcpServerService.getDiscoveredTools(id);
    }

    @GetMapping("/groups/{groupId}/mcp-tools")
    public Flux<McpToolDto> getGroupToolAccess(@PathVariable String groupId) {
        return mcpServerService.getGroupToolAccess(groupId);
    }

    @PutMapping("/groups/{groupId}/mcp-tools")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> updateGroupToolAccess(@PathVariable String groupId, @RequestBody List<GroupToolAccessRequest> updates) {
        return mcpServerService.updateGroupToolAccess(groupId, updates);
    }
}

package com.ppgpt.gateway.controller;

import com.ppgpt.gateway.dto.CreateMcpServerRequest;
import com.ppgpt.gateway.dto.McpServerDto;
import com.ppgpt.gateway.service.McpServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/mcp-servers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class McpServerController {

    private final McpServerService mcpServerService;

    @GetMapping
    public Flux<McpServerDto> getAllMcpServers() {
        return mcpServerService.getAllMcpServers();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<McpServerDto> createMcpServer(@Valid @RequestBody CreateMcpServerRequest request) {
        return mcpServerService.createMcpServer(request);
    }

    @PutMapping("/{id}")
    public Mono<McpServerDto> updateMcpServer(@PathVariable String id, @Valid @RequestBody CreateMcpServerRequest request) {
        return mcpServerService.updateMcpServer(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteMcpServer(@PathVariable String id) {
        return mcpServerService.deleteMcpServer(id);
    }

    @PostMapping("/{id}/test")
    public Mono<Map<String, Object>> testConnection(@PathVariable String id) {
        return mcpServerService.testConnection(id);
    }
}

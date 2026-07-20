package com.ppgpt.gateway.controller;

import com.ppgpt.gateway.dto.*;
import com.ppgpt.gateway.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ─── Models ──────────────────────────────────────────────────────────────

    @GetMapping("/models")
    public Flux<ModelDto> listModels() {
        return adminService.listModels();
    }

    @GetMapping("/models/{id}")
    public Mono<ModelDto> getModel(@PathVariable String id) {
        return adminService.getModel(id);
    }

    @PostMapping("/models")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ModelDto> createModel(@RequestBody ModelDto dto) {
        return adminService.createModel(dto);
    }

    @PutMapping("/models/{id}")
    public Mono<ModelDto> updateModel(@PathVariable String id, @RequestBody ModelDto dto) {
        return adminService.updateModel(id, dto);
    }

    @DeleteMapping("/models/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteModel(@PathVariable String id) {
        return adminService.deleteModel(id);
    }

    // ─── Groups ──────────────────────────────────────────────────────────────

    @GetMapping("/groups")
    public Flux<GroupDto> listGroups() {
        return adminService.listGroups();
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<GroupDto> createGroup(@RequestBody GroupDto dto) {
        return adminService.createGroup(dto);
    }

    @PutMapping("/groups/{id}")
    public Mono<GroupDto> updateGroup(@PathVariable String id, @RequestBody GroupDto dto) {
        return adminService.updateGroup(id, dto);
    }

    @DeleteMapping("/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteGroup(@PathVariable String id) {
        return adminService.deleteGroup(id);
    }

    // ─── Credit Rates ─────────────────────────────────────────────────────────

    @GetMapping("/credits")
    public Flux<CreditRateDto> listCreditRates() {
        return adminService.listCreditRates();
    }

    @PostMapping("/credits")
    public Mono<CreditRateDto> upsertCreditRate(@RequestBody CreditRateDto dto) {
        return adminService.upsertCreditRate(dto);
    }

    @DeleteMapping("/credits/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteCreditRate(@PathVariable String id) {
        return adminService.deleteCreditRate(id);
    }

    // ─── Dashboard Analytics ──────────────────────────────────────────

    @GetMapping("/dashboard/analytics")
    public Flux<com.ppgpt.gateway.dto.AnalyticsDto> getAnalytics(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        return adminService.getAnalytics(startDate, endDate);
    }

    // ─── Users ───────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public Flux<UserDto> listUsers() {
        return adminService.listUsers();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserDto> createUser(@RequestBody UserDto dto) {
        return adminService.createUser(dto);
    }

    @PutMapping("/users/{id}")
    public Mono<UserDto> updateUser(@PathVariable String id, @RequestBody UserDto dto) {
        return adminService.updateUser(id, dto);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteUser(@PathVariable String id) {
        return adminService.deleteUser(id);
    }

    // ─── Audit Logs (read-only) ───────────────────────────────────────────────

    @GetMapping("/audit-logs")
    public Mono<PageResponse<AuditLogDto>> auditLogs(
            @RequestParam(required = false) String modelId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.listAuditLogs(modelId, startDate, endDate, page, size);
    }
}

package com.ppgpt.gateway.controller;

import com.ppgpt.gateway.dto.CreditRateDto;
import com.ppgpt.gateway.dto.GroupDto;
import com.ppgpt.gateway.dto.ModelDto;
import com.ppgpt.gateway.dto.UserDto;
import com.ppgpt.gateway.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Map;

/**
 * REST Controller for Admin endpoints (Models, Groups, Credit Rates, Users, Dashboard Analytics, Audit Logs).
 */
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
    public Mono<Map<String, Object>> getAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
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

    // ─── Audit Logs ───────────────────────────────────────────────────────────

    @GetMapping("/audit-logs")
    public Mono<Map<String, Object>> auditLogs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.getAuditLogs(search, startDate, endDate, page, size);
    }
}

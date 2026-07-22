package com.ppgpt.gateway.service;

import com.ppgpt.gateway.domain.*;
import com.ppgpt.gateway.dto.*;
import com.ppgpt.gateway.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin CRUD service for models, groups, credit rates, users, and audit logs.
 * All operations return Mono/Flux — no blocking calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final ModelRepository modelRepository;
    private final UserGroupRepository userGroupRepository;
    private final GroupModelAccessRepository groupModelAccessRepository;
    private final CreditRateRepository creditRateRepository;
    private final UserRepository userRepository;
    private final DashboardMetricRepository dashboardMetricRepository;
    private final CryptoService cryptoService;
    private final PasswordEncoder passwordEncoder;
    private final R2dbcEntityTemplate entityTemplate;

    // ─── Models ──────────────────────────────────────────────────────────────

    public Flux<ModelDto> listModels() {
        return modelRepository.findAll().map(this::toModelDto);
    }

    public Mono<ModelDto> getModel(String id) {
        return modelRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found")))
                .map(this::toModelDto);
    }

    public Mono<ModelDto> createModel(ModelDto dto) {
        String encrypted = cryptoService.encrypt(dto.getCredentials());
        Model model = Model.builder()
                .id(UUID.randomUUID().toString())
                .name(dto.getName())
                .provider(dto.getProvider())
                .modelName(dto.getModelName())
                .endpointUrl(dto.getEndpointUrl())
                .credentialsEncrypted(encrypted)
                .isActive(dto.isActive())
                .timeoutMs(dto.getTimeoutMs() > 0 ? dto.getTimeoutMs() : 30000)
                .temperature(dto.getTemperature())
                .systemPrompt(dto.getSystemPrompt())
                .maxHistoryMessages(dto.getMaxHistoryMessages() > 0 ? dto.getMaxHistoryMessages() : 10)
                .modelType(dto.getModelType() != null ? dto.getModelType() : "GENERATION")
                .supportsVision(dto.isSupportsVision())
                .supportsTools(dto.isSupportsTools())
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        return entityTemplate.insert(model).map(this::toModelDto);
    }

    public Mono<ModelDto> updateModel(String id, ModelDto dto) {
        return modelRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found")))
                .flatMap(existing -> {
                    if (dto.getName() != null)
                        existing.setName(dto.getName());
                    existing.setProvider(dto.getProvider());
                    existing.setModelName(dto.getModelName());
                    existing.setEndpointUrl(dto.getEndpointUrl());
                    existing.setActive(dto.isActive());
                    existing.setTimeoutMs(dto.getTimeoutMs());
                    existing.setTemperature(dto.getTemperature());
                    if (dto.getSystemPrompt() != null)
                        existing.setSystemPrompt(dto.getSystemPrompt());
                    if (dto.getMaxHistoryMessages() > 0)
                        existing.setMaxHistoryMessages(dto.getMaxHistoryMessages());
                    if (dto.getModelType() != null)
                        existing.setModelType(dto.getModelType());
                    existing.setSupportsVision(dto.isSupportsVision());
                    existing.setSupportsTools(dto.isSupportsTools());
                    if (dto.getCredentials() != null && !dto.getCredentials().isBlank()) {
                        existing.setCredentialsEncrypted(cryptoService.encrypt(dto.getCredentials()));
                    }
                    return modelRepository.save(existing);
                })
                .map(this::toModelDto);
    }

    public Mono<Void> deleteModel(String id) {
        return modelRepository.deleteById(id);
    }

    // ─── Groups ──────────────────────────────────────────────────────────────

    public Flux<GroupDto> listGroups() {
        return userGroupRepository.findAll()
                .flatMap(group -> groupModelAccessRepository.findByGroupId(group.getId())
                        .map(GroupModelAccess::getModelId)
                        .collectList()
                        .map(modelIds -> GroupDto.builder()
                                .id(group.getId())
                                .groupName(group.getGroupName())
                                .maxDailyCredits(group.getMaxDailyCredits())
                                .guardrailModelId(group.getGuardrailModelId())
                                .allowedModelIds(modelIds)
                                .build()));
    }

    public Mono<GroupDto> createGroup(GroupDto dto) {
        UserGroup group = UserGroup.builder()
                .id(UUID.randomUUID().toString())
                .groupName(dto.getGroupName())
                .maxDailyCredits(dto.getMaxDailyCredits() != null ? dto.getMaxDailyCredits()
                        : java.math.BigDecimal.valueOf(10000))
                .guardrailModelId(dto.getGuardrailModelId())
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        return entityTemplate.insert(group)
                .flatMap(saved -> assignModels(saved.getId(), dto.getAllowedModelIds())
                        .thenReturn(saved))
                .map(saved -> dto.toBuilder().id(saved.getId()).build());
    }

    @Transactional
    public Mono<GroupDto> updateGroup(String id, GroupDto dto) {
        return userGroupRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found")))
                .flatMap(group -> {
                    group.setGroupName(dto.getGroupName());
                    group.setMaxDailyCredits(dto.getMaxDailyCredits());
                    group.setGuardrailModelId(dto.getGuardrailModelId());
                    return userGroupRepository.save(group);
                })
                .flatMap(saved -> groupModelAccessRepository.deleteByGroupId(id)
                        .then(assignModels(id, dto.getAllowedModelIds()))
                        .thenReturn(saved))
                .map(saved -> dto.toBuilder().id(saved.getId()).build());
    }

    public Mono<Void> deleteGroup(String id) {
        return userGroupRepository.deleteById(id);
    }

    // ─── Credit Rates ─────────────────────────────────────────────────────────

    public Flux<CreditRateDto> listCreditRates() {
        return creditRateRepository.findAll()
                .flatMap(rate -> modelRepository.findById(rate.getModelId())
                        .map(model -> CreditRateDto.builder()
                                .id(rate.getId())
                                .modelId(rate.getModelId())
                                .modelName(model.getModelName())
                                .inputMultiplier(rate.getInputMultiplier())
                                .outputMultiplier(rate.getOutputMultiplier())
                                .build()));
    }

    public Mono<CreditRateDto> upsertCreditRate(CreditRateDto dto) {
        return creditRateRepository.findByModelId(dto.getModelId())
                .flatMap(existing -> {
                    existing.setInputMultiplier(dto.getInputMultiplier());
                    existing.setOutputMultiplier(dto.getOutputMultiplier());
                    return creditRateRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    CreditRate rate = CreditRate.builder()
                            .id(UUID.randomUUID().toString())
                            .modelId(dto.getModelId())
                            .inputMultiplier(dto.getInputMultiplier())
                            .outputMultiplier(dto.getOutputMultiplier())
                            .build();
                    return entityTemplate.insert(rate);
                }))
                .map(r -> dto.toBuilder().id(r.getId()).build());
    }

    public Mono<Void> deleteCreditRate(String id) {
        return creditRateRepository.deleteById(id);
    }

    // ─── Analytics Dashboard ───────────────────────────────────────────

    public Flux<com.ppgpt.gateway.dto.AnalyticsDto> getAnalytics(java.time.LocalDate startDate,
            java.time.LocalDate endDate) {
        Flux<DashboardMetric> metricFlux;
        if (startDate != null && endDate != null) {
            metricFlux = dashboardMetricRepository.findByUsageDateBetween(startDate, endDate);
        } else if (startDate != null) {
            metricFlux = dashboardMetricRepository.findByUsageDateGreaterThanEqual(startDate);
        } else if (endDate != null) {
            metricFlux = dashboardMetricRepository.findByUsageDateLessThanEqual(endDate);
        } else {
            metricFlux = dashboardMetricRepository.findAll();
        }

        return metricFlux
                // B5: Use '|' as separator — it cannot appear in UUIDs (which use only hex +
                // '-')
                .groupBy(m -> m.getGroupId() + "|" + m.getModelId())
                .flatMap(groupFlux -> {
                    // Capture key before reduce so it is not lost under backpressure
                    String compositeKey = groupFlux.key();
                    return groupFlux.reduce(
                            new long[] { 0L, 0L },
                            (acc, curr) -> new long[] { acc[0] + curr.getTotalInputTokens(),
                                    acc[1] + curr.getTotalOutputTokens() })
                            .map(totals -> Map.entry(compositeKey, totals));
                })
                .flatMap(entry -> {
                    // B5 fix: key is now "groupId|modelId" — split on '|'
                    String[] parts = entry.getKey().split("\\|", 2);
                    String groupId = parts[0];
                    String modelId = parts[1];
                    long inTokens = entry.getValue()[0];
                    long outTokens = entry.getValue()[1];

                    Mono<String> groupNameMono = userGroupRepository.findById(groupId)
                            .map(UserGroup::getGroupName)
                            .defaultIfEmpty("Unknown Group");

                    Mono<String> modelNameMono = modelRepository.findById(modelId)
                            .map(m -> m.getName() != null && !m.getName().isBlank() ? m.getName() : m.getModelName())
                            .defaultIfEmpty("Unknown Model");

                    Mono<CreditRate> creditRateMono = creditRateRepository.findByModelId(modelId)
                            .defaultIfEmpty(CreditRate.builder()
                                    .inputMultiplier(BigDecimal.ONE)
                                    .outputMultiplier(BigDecimal.valueOf(2))
                                    .build());

                    return Mono.zip(groupNameMono, modelNameMono, creditRateMono)
                            .map(tuple -> {
                                String groupName = tuple.getT1();
                                String modelName = tuple.getT2();
                                CreditRate rate = tuple.getT3();

                                BigDecimal inMult = rate.getInputMultiplier() != null ? rate.getInputMultiplier()
                                        : BigDecimal.ONE;
                                BigDecimal outMult = rate.getOutputMultiplier() != null ? rate.getOutputMultiplier()
                                        : BigDecimal.valueOf(2);

                                BigDecimal inCredits = BigDecimal.valueOf(inTokens).multiply(inMult);
                                BigDecimal outCredits = BigDecimal.valueOf(outTokens).multiply(outMult);
                                BigDecimal totalCredits = inCredits.add(outCredits);

                                return AnalyticsDto.builder()
                                        .groupId(groupId)
                                        .groupName(groupName)
                                        .modelId(modelId)
                                        .modelName(modelName)
                                        .totalInputTokens(inTokens)
                                        .totalOutputTokens(outTokens)
                                        .totalTokens(inTokens + outTokens)
                                        .totalCredits(totalCredits)
                                        .build();
                            });
                });
    }

    // ─── Users ───────────────────────────────────────────────────────────────

    public Flux<UserDto> listUsers() {
        return userRepository.findAll()
                .flatMap(user -> userGroupRepository.findById(user.getGroupId() != null ? user.getGroupId() : "")
                        .map(group -> toUserDto(user, group.getGroupName()))
                        .defaultIfEmpty(toUserDto(user, "N/A")));
    }

    public Mono<UserDto> createUser(UserDto dto) {
        return userRepository.existsByUsername(dto.getUsername())
                .filter(exists -> !exists)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.CONFLICT, "Username already exists")))
                .flatMap(ok -> {
                    String hash = passwordEncoder.encode(dto.getPassword());
                    User user = User.builder()
                            .id(UUID.randomUUID().toString())
                            .username(dto.getUsername())
                            .email(dto.getEmail())
                            .passwordHash(hash)
                            .authSource(dto.getAuthSource() != null ? dto.getAuthSource() : "LOCAL")
                            .groupId(dto.getGroupId())
                            .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                            .build();
                    return entityTemplate.insert(user);
                })
                .map(u -> toUserDto(u, null));
    }

    public Mono<UserDto> updateUser(String id, UserDto dto) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")))
                .flatMap(user -> {
                    user.setEmail(dto.getEmail());
                    user.setGroupId(dto.getGroupId());
                    if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
                        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
                    }
                    return userRepository.save(user);
                })
                .map(u -> toUserDto(u, null));
    }

    public Mono<Void> deleteUser(String id) {
        return userRepository.deleteById(id);
    }

    // ─── Audit Logs ──────────────────────────────────────────────────────────

    public Mono<PageResponse<AuditLogDto>> listAuditLogs(
            String modelId,
            String startDateStr,
            String endDateStr,
            int page,
            int size) {

        Criteria criteria = Criteria.empty();
        if (modelId != null && !modelId.isBlank()) {
            criteria = criteria.and("model_id").is(modelId);
        }
        if (startDateStr != null && !startDateStr.isBlank()) {
            LocalDateTime start = LocalDate.parse(startDateStr).atStartOfDay();
            criteria = criteria.and("created_at").greaterThanOrEquals(start);
        }
        if (endDateStr != null && !endDateStr.isBlank()) {
            LocalDateTime end = LocalDate.parse(endDateStr).atTime(LocalTime.MAX);
            criteria = criteria.and("created_at").lessThanOrEquals(end);
        }

        Query query = Query.query(criteria);
        Mono<Long> totalMono = entityTemplate.count(query, ChatLog.class);

        Query pageableQuery = query.sort(Sort.by(Sort.Direction.DESC, "created_at"))
                .limit(size)
                .offset((long) page * size);

        Flux<AuditLogDto> contentFlux = entityTemplate.select(pageableQuery, ChatLog.class)
                .flatMap(logRecord -> Mono.zip(
                        userRepository.findById(logRecord.getUserId())
                                .map(User::getUsername)
                                .defaultIfEmpty("Unknown User"),
                        logRecord.getModelDisplayName() != null && !logRecord.getModelDisplayName().isBlank()
                                ? Mono.just(logRecord.getModelDisplayName())
                                : (logRecord.getModelId() != null
                                        ? modelRepository.findById(logRecord.getModelId())
                                                .map(m -> m.getName() != null && !m.getName().isBlank() ? m.getName()
                                                        : m.getModelName())
                                                .defaultIfEmpty("Deleted Model")
                                        : Mono.just("Deleted Model")))
                        .map(tuple -> AuditLogDto.builder()
                                .id(logRecord.getId())
                                .userId(logRecord.getUserId())
                                .username(tuple.getT1())
                                .modelId(logRecord.getModelId())
                                .modelDisplayName(tuple.getT2())
                                .sessionId(logRecord.getSessionId())
                                .prompt(logRecord.getPrompt())
                                .response(logRecord.getResponse())
                                .createdAt(logRecord.getCreatedAt())
                                .build()));

        return Mono.zip(contentFlux.collectList(), totalMono)
                .map(tuple -> {
                    List<AuditLogDto> content = tuple.getT1();
                    long total = tuple.getT2();
                    int totalPages = (int) Math.ceil((double) total / (size > 0 ? size : 1));
                    return PageResponse.<AuditLogDto>builder()
                            .content(content)
                            .page(page)
                            .size(size)
                            .totalElements(total)
                            .totalPages(totalPages)
                            .build();
                });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private Mono<Void> assignModels(String groupId, List<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty())
            return Mono.empty();
        List<GroupModelAccess> accesses = modelIds.stream()
                .map(modelId -> GroupModelAccess.builder()
                        .id(UUID.randomUUID().toString())
                        .groupId(groupId)
                        .modelId(modelId)
                        .build())
                .toList();
        return reactor.core.publisher.Flux.fromIterable(accesses)
                .flatMap(entityTemplate::insert)
                .then();
    }

    private ModelDto toModelDto(Model m) {
        return ModelDto.builder()
                .id(m.getId())
                .name(m.getName())
                .provider(m.getProvider())
                .modelName(m.getModelName())
                .endpointUrl(m.getEndpointUrl())
                .isActive(m.isActive())
                .timeoutMs(m.getTimeoutMs())
                .temperature(m.getTemperature())
                .systemPrompt(m.getSystemPrompt())
                .maxHistoryMessages(m.getMaxHistoryMessages())
                .modelType(m.getModelType())
                .supportsVision(m.isSupportsVision())
                .supportsTools(m.isSupportsTools())
                // credentials intentionally NOT returned
                .build();
    }

    private UserDto toUserDto(User u, String groupName) {
        return UserDto.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .authSource(u.getAuthSource())
                .groupId(u.getGroupId())
                .groupName(groupName)
                .build();
    }
}

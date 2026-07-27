package com.ppgpt.gateway.service;

import com.ppgpt.gateway.domain.ChatLog;
import com.ppgpt.gateway.domain.CreditRate;
import com.ppgpt.gateway.domain.DashboardMetric;
import com.ppgpt.gateway.domain.GroupModelAccess;
import com.ppgpt.gateway.domain.Model;
import com.ppgpt.gateway.domain.User;
import com.ppgpt.gateway.domain.UserGroup;
import com.ppgpt.gateway.dto.CreditRateDto;
import com.ppgpt.gateway.dto.GroupDto;
import com.ppgpt.gateway.dto.ModelDto;
import com.ppgpt.gateway.dto.UserDto;
import com.ppgpt.gateway.repository.CreditRateRepository;
import com.ppgpt.gateway.repository.DashboardMetricRepository;
import com.ppgpt.gateway.repository.GroupModelAccessRepository;
import com.ppgpt.gateway.repository.ModelRepository;
import com.ppgpt.gateway.repository.UserGroupRepository;
import com.ppgpt.gateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service managing administrative operations: models, user groups, credit rates, user accounts, audit logs, and executive dashboard metrics.
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

    /**
     * Lists all registered AI models.
     *
     * @return Flux of model DTOs
     */
    public Flux<ModelDto> listModels() {
        return modelRepository.findAll().map(this::toModelDto);
    }

    /**
     * Gets an AI model definition by ID.
     *
     * @param id Model ID
     * @return Mono emitting model DTO
     */
    public Mono<ModelDto> getModel(String id) {
        return modelRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found")))
                .map(this::toModelDto);
    }

    /**
     * Creates a new AI model definition.
     *
     * @param dto Model definition payload
     * @return Mono emitting created model DTO
     */
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

    /**
     * Updates an existing AI model definition.
     *
     * @param id  Model ID
     * @param dto Updated model payload
     * @return Mono emitting updated model DTO
     */
    public Mono<ModelDto> updateModel(String id, ModelDto dto) {
        return modelRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found")))
                .flatMap(existing -> {
                    if (dto.getName() != null) existing.setName(dto.getName());
                    existing.setProvider(dto.getProvider());
                    existing.setModelName(dto.getModelName());
                    existing.setEndpointUrl(dto.getEndpointUrl());
                    existing.setActive(dto.isActive());
                    existing.setTimeoutMs(dto.getTimeoutMs());
                    existing.setTemperature(dto.getTemperature());
                    if (dto.getSystemPrompt() != null) existing.setSystemPrompt(dto.getSystemPrompt());
                    if (dto.getMaxHistoryMessages() > 0) existing.setMaxHistoryMessages(dto.getMaxHistoryMessages());
                    if (dto.getModelType() != null) existing.setModelType(dto.getModelType());
                    existing.setSupportsVision(dto.isSupportsVision());
                    existing.setSupportsTools(dto.isSupportsTools());
                    if (dto.getCredentials() != null && !dto.getCredentials().isBlank()) {
                        existing.setCredentialsEncrypted(cryptoService.encrypt(dto.getCredentials()));
                    }
                    return modelRepository.save(existing);
                })
                .map(this::toModelDto);
    }

    /**
     * Deletes an AI model definition.
     *
     * @param id Model ID
     * @return Mono completing upon deletion
     */
    public Mono<Void> deleteModel(String id) {
        return modelRepository.deleteById(id);
    }

    // ─── Groups ──────────────────────────────────────────────────────────────

    /**
     * Lists all user groups and their allowed model permissions.
     *
     * @return Flux of GroupDto
     */
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

    /**
     * Creates a new user group with specified model access list.
     *
     * @param dto Group configuration payload
     * @return Mono emitting created group DTO
     */
    public Mono<GroupDto> createGroup(GroupDto dto) {
        UserGroup group = UserGroup.builder()
                .id(UUID.randomUUID().toString())
                .groupName(dto.getGroupName())
                .maxDailyCredits(dto.getMaxDailyCredits() != null ? dto.getMaxDailyCredits() : BigDecimal.valueOf(10000))
                .guardrailModelId(dto.getGuardrailModelId())
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        return entityTemplate.insert(group)
                .flatMap(saved -> assignModels(saved.getId(), dto.getAllowedModelIds()).thenReturn(saved))
                .map(saved -> dto.toBuilder().id(saved.getId()).build());
    }

    /**
     * Updates an existing user group configuration.
     *
     * @param id  Group ID
     * @param dto Updated group payload
     * @return Mono emitting updated group DTO
     */
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

    /**
     * Deletes a user group.
     *
     * @param id Group ID
     * @return Mono completing upon deletion
     */
    public Mono<Void> deleteGroup(String id) {
        return userGroupRepository.deleteById(id);
    }

    // ─── Credit Rates ─────────────────────────────────────────────────────────

    /**
     * Lists credit multipliers for all registered models.
     *
     * @return Flux of CreditRateDto
     */
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

    /**
     * Upserts credit rate multipliers for a specific model.
     *
     * @param dto Credit rate payload
     * @return Mono emitting upserted credit rate DTO
     */
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

    /**
     * Deletes a credit rate configuration.
     *
     * @param id Credit rate ID
     * @return Mono completing upon deletion
     */
    public Mono<Void> deleteCreditRate(String id) {
        return creditRateRepository.deleteById(id);
    }

    // ─── Users ────────────────────────────────────────────────────────────────

    /**
     * Lists all registered user accounts.
     *
     * @return Flux of UserDto
     */
    public Flux<UserDto> listUsers() {
        return userRepository.findAll()
                .flatMap(user -> userGroupRepository.findById(user.getGroupId())
                        .map(group -> UserDto.builder()
                                .id(user.getId())
                                .username(user.getUsername())
                                .email(user.getEmail())
                                .authSource(user.getAuthSource())
                                .groupId(user.getGroupId())
                                .groupName(group.getGroupName())
                                .createdAt(user.getCreatedAt())
                                .build()));
    }

    /**
     * Creates a new local user account.
     *
     * @param dto User creation request
     * @return Mono emitting created UserDto
     */
    public Mono<UserDto> createUser(UserDto dto) {
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required for local users"));
        }

        String hashed = passwordEncoder.encode(dto.getPassword());
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(dto.getUsername().trim().toLowerCase())
                .email(dto.getEmail())
                .passwordHash(hashed)
                .authSource("LOCAL")
                .groupId(dto.getGroupId())
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();

        return entityTemplate.insert(user)
                .flatMap(saved -> userGroupRepository.findById(saved.getGroupId())
                        .map(group -> UserDto.builder()
                                .id(saved.getId())
                                .username(saved.getUsername())
                                .email(saved.getEmail())
                                .authSource(saved.getAuthSource())
                                .groupId(saved.getGroupId())
                                .groupName(group.getGroupName())
                                .createdAt(saved.getCreatedAt())
                                .build()));
    }

    /**
     * Updates an existing user's group or password.
     *
     * @param id  User ID
     * @param dto Update user request payload
     * @return Mono emitting updated UserDto
     */
    public Mono<UserDto> updateUser(String id, UserDto dto) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")))
                .flatMap(existing -> {
                    if (dto.getEmail() != null) existing.setEmail(dto.getEmail());
                    if (dto.getGroupId() != null) existing.setGroupId(dto.getGroupId());
                    if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
                        existing.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
                    }
                    return userRepository.save(existing);
                })
                .flatMap(saved -> userGroupRepository.findById(saved.getGroupId())
                        .map(group -> UserDto.builder()
                                .id(saved.getId())
                                .username(saved.getUsername())
                                .email(saved.getEmail())
                                .authSource(saved.getAuthSource())
                                .groupId(saved.getGroupId())
                                .groupName(group.getGroupName())
                                .createdAt(saved.getCreatedAt())
                                .build()));
    }

    /**
     * Deletes a user account.
     *
     * @param id User ID
     * @return Mono completing upon deletion
     */
    public Mono<Void> deleteUser(String id) {
        return userRepository.deleteById(id);
    }

    // ─── Audit Logs & Analytics ────────────────────────────────────────────────

    /**
     * Retrieves paginated audit logs with optional filtering.
     *
     * @param search    Username search keyword
     * @param startDate Optional start date filter
     * @param endDate   Optional end date filter
     * @param page      Page index (0-based)
     * @param size      Page size
     * @return Mono emitting paginated Map of items and total count
     */
    public Mono<Map<String, Object>> getAuditLogs(String search, LocalDate startDate, LocalDate endDate, int page, int size) {
        Criteria criteria = Criteria.empty();

        if (startDate != null) {
            criteria = criteria.and("created_at").greaterThanOrEquals(startDate.atStartOfDay());
        }
        if (endDate != null) {
            criteria = criteria.and("created_at").lessThanOrEquals(endDate.atTime(LocalTime.MAX));
        }

        final Criteria finalCriteria = criteria;
        Query query = Query.query(finalCriteria)
                .sort(Sort.by(Sort.Direction.DESC, "created_at"))
                .limit(size)
                .offset((long) page * size);

        return entityTemplate.select(query, ChatLog.class)
                .flatMap(log -> userRepository.findById(log.getUserId())
                        .map(User::getUsername)
                        .defaultIfEmpty("Unknown User")
                        .map(uname -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("id", log.getId() != null ? log.getId() : "");
                            map.put("userId", log.getUserId() != null ? log.getUserId() : "");
                            map.put("username", uname);
                            map.put("modelId", log.getModelId() != null ? log.getModelId() : "");
                            map.put("modelDisplayName", log.getModelDisplayName() != null ? log.getModelDisplayName() : log.getModelId());
                            map.put("sessionId", log.getSessionId() != null ? log.getSessionId() : "");
                            map.put("prompt", log.getPrompt() != null ? log.getPrompt() : "");
                            map.put("response", log.getResponse() != null ? log.getResponse() : "");
                            map.put("createdAt", log.getCreatedAt());
                            return map;
                        }))
                .filter(item -> {
                    if (search == null || search.isBlank()) return true;
                    String uname = (String) item.get("username");
                    String promptText = (String) item.get("prompt");
                    String responseText = (String) item.get("response");
                    String q = search.toLowerCase();
                    return (uname != null && uname.toLowerCase().contains(q))
                            || (promptText != null && promptText.toLowerCase().contains(q))
                            || (responseText != null && responseText.toLowerCase().contains(q));
                })
                .collectList()
                .flatMap(list -> entityTemplate.count(Query.query(finalCriteria), ChatLog.class)
                        .map(total -> {
                            int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 1;
                            Map<String, Object> result = new HashMap<>();
                            result.put("items", list);
                            result.put("content", list);
                            result.put("total", total);
                            result.put("totalElements", total);
                            result.put("totalPages", totalPages > 0 ? totalPages : 1);
                            result.put("page", page);
                            result.put("size", size);
                            return result;
                        }));
    }

    /**
     * Retrieves aggregated executive dashboard metrics for a date range.
     *
     * @param startDate Optional start date
     * @param endDate   Optional end date
     * @return Mono emitting analytics metrics payload
     */
    public Mono<Map<String, Object>> getAnalytics(LocalDate startDate, LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now(ZoneOffset.UTC).minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now(ZoneOffset.UTC);

        return dashboardMetricRepository.findByUsageDateBetween(start, end)
                .flatMap(metric -> Mono.zip(
                        userGroupRepository.findById(metric.getGroupId()).map(UserGroup::getGroupName).defaultIfEmpty("Default Group"),
                        modelRepository.findById(metric.getModelId()).map(Model::getName).defaultIfEmpty(metric.getModelId())
                ).map(tuple -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", metric.getId());
                    item.put("groupId", metric.getGroupId());
                    item.put("groupName", tuple.getT1());
                    item.put("modelId", metric.getModelId());
                    item.put("modelName", tuple.getT2());
                    item.put("usageDate", metric.getUsageDate());
                    item.put("totalInputTokens", metric.getTotalInputTokens());
                    item.put("totalOutputTokens", metric.getTotalOutputTokens());
                    item.put("totalTokens", metric.getTotalInputTokens() + metric.getTotalOutputTokens());
                    item.put("totalCredits", (metric.getTotalInputTokens() + metric.getTotalOutputTokens()) * 0.001);
                    return item;
                }))
                .collectList()
                .flatMap(metricsList -> {
                    if (metricsList.isEmpty()) {
                        // Fallback: Aggregate live metrics from chat_log if dashboard_metrics table is empty
                        return aggregateLiveAnalyticsFromChatLogs(start, end);
                    }

                    long totalInput = metricsList.stream().mapToLong(m -> ((Number) m.get("totalInputTokens")).longValue()).sum();
                    long totalOutput = metricsList.stream().mapToLong(m -> ((Number) m.get("totalOutputTokens")).longValue()).sum();

                    Map<String, Object> result = new HashMap<>();
                    result.put("startDate", start);
                    result.put("endDate", end);
                    result.put("totalInputTokens", totalInput);
                    result.put("totalOutputTokens", totalOutput);
                    result.put("totalTokens", totalInput + totalOutput);
                    result.put("metrics", metricsList);
                    result.put("items", metricsList);
                    return Mono.just(result);
                });
    }

    private Mono<Map<String, Object>> aggregateLiveAnalyticsFromChatLogs(LocalDate start, LocalDate end) {
        Criteria criteria = Criteria.where("created_at")
                .greaterThanOrEquals(start.atStartOfDay())
                .and("created_at").lessThanOrEquals(end.atTime(LocalTime.MAX));

        return entityTemplate.select(Query.query(criteria), ChatLog.class)
                .flatMap(log -> userRepository.findById(log.getUserId())
                        .flatMap(u -> userGroupRepository.findById(u.getGroupId())
                                .map(g -> Map.entry(g.getGroupName(), log)))
                        .defaultIfEmpty(Map.entry("Default Group", log)))
                .collectList()
                .flatMap(entries -> {
                    Map<String, Map<String, Object>> aggMap = new HashMap<>();

                    for (Map.Entry<String, ChatLog> entry : entries) {
                        String groupName = entry.getKey();
                        ChatLog log = entry.getValue();
                        String modelName = log.getModelDisplayName() != null ? log.getModelDisplayName() : log.getModelId();
                        String key = groupName + "___" + modelName;

                        long inputEst = log.getPrompt() != null ? log.getPrompt().length() / 4L : 0L;
                        long outputEst = log.getResponse() != null ? log.getResponse().length() / 4L : 0L;

                        Map<String, Object> row = aggMap.computeIfAbsent(key, k -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("groupId", "g-default");
                            m.put("groupName", groupName);
                            m.put("modelId", log.getModelId());
                            m.put("modelName", modelName);
                            m.put("totalInputTokens", 0L);
                            m.put("totalOutputTokens", 0L);
                            m.put("totalTokens", 0L);
                            m.put("totalCredits", 0.0);
                            return m;
                        });

                        long currIn = ((Number) row.get("totalInputTokens")).longValue() + inputEst;
                        long currOut = ((Number) row.get("totalOutputTokens")).longValue() + outputEst;
                        long totalTok = currIn + currOut;

                        row.put("totalInputTokens", currIn);
                        row.put("totalOutputTokens", currOut);
                        row.put("totalTokens", totalTok);
                        row.put("totalCredits", totalTok * 0.001);
                    }

                    List<Map<String, Object>> metricsList = new ArrayList<>(aggMap.values());
                    long totalInput = metricsList.stream().mapToLong(m -> ((Number) m.get("totalInputTokens")).longValue()).sum();
                    long totalOutput = metricsList.stream().mapToLong(m -> ((Number) m.get("totalOutputTokens")).longValue()).sum();

                    Map<String, Object> result = new HashMap<>();
                    result.put("startDate", start);
                    result.put("endDate", end);
                    result.put("totalInputTokens", totalInput);
                    result.put("totalOutputTokens", totalOutput);
                    result.put("totalTokens", totalInput + totalOutput);
                    result.put("metrics", metricsList);
                    result.put("items", metricsList);
                    return Mono.just(result);
                });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Mono<Void> assignModels(String groupId, List<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) return Mono.empty();
        return Flux.fromIterable(modelIds)
                .flatMap(modelId -> entityTemplate.insert(GroupModelAccess.builder()
                        .id(UUID.randomUUID().toString())
                        .groupId(groupId)
                        .modelId(modelId)
                        .build()))
                .then();
    }

    private ModelDto toModelDto(Model model) {
        return ModelDto.builder()
                .id(model.getId())
                .name(model.getName())
                .provider(model.getProvider())
                .modelName(model.getModelName())
                .endpointUrl(model.getEndpointUrl())
                .isActive(model.isActive())
                .timeoutMs(model.getTimeoutMs())
                .temperature(model.getTemperature())
                .systemPrompt(model.getSystemPrompt())
                .maxHistoryMessages(model.getMaxHistoryMessages())
                .modelType(model.getModelType())
                .supportsVision(model.isSupportsVision())
                .supportsTools(model.isSupportsTools())
                .hasCredentials(model.getCredentialsEncrypted() != null && !model.getCredentialsEncrypted().isBlank())
                .build();
    }
}

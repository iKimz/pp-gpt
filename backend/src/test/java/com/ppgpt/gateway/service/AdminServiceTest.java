package com.ppgpt.gateway.service;

import com.ppgpt.gateway.domain.CreditRate;
import com.ppgpt.gateway.domain.GroupModelAccess;
import com.ppgpt.gateway.domain.Model;
import com.ppgpt.gateway.domain.User;
import com.ppgpt.gateway.domain.UserGroup;
import com.ppgpt.gateway.dto.CreditRateDto;
import com.ppgpt.gateway.dto.ModelDto;
import com.ppgpt.gateway.dto.UserDto;
import com.ppgpt.gateway.repository.CreditRateRepository;
import com.ppgpt.gateway.repository.DashboardMetricRepository;
import com.ppgpt.gateway.repository.GroupModelAccessRepository;
import com.ppgpt.gateway.repository.ModelRepository;
import com.ppgpt.gateway.repository.UserGroupRepository;
import com.ppgpt.gateway.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminServiceTest {

    @Mock
    private ModelRepository modelRepository;
    @Mock
    private UserGroupRepository userGroupRepository;
    @Mock
    private GroupModelAccessRepository groupModelAccessRepository;
    @Mock
    private CreditRateRepository creditRateRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DashboardMetricRepository dashboardMetricRepository;
    @Mock
    private CryptoService cryptoService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private R2dbcEntityTemplate entityTemplate;

    @InjectMocks
    private AdminService adminService;

    @Test
    @DisplayName("listModels: Returns all registered models as DTOs")
    public void testListModels() {
        Model model1 = Model.builder().id("m-1").name("GPT-4o").provider("OPENAI").modelName("gpt-4o").isActive(true).build();
        Model model2 = Model.builder().id("m-2").name("Claude Sonnet").provider("AWS_BEDROCK").modelName("claude-3-5-sonnet").isActive(true).build();

        when(modelRepository.findAll()).thenReturn(Flux.just(model1, model2));

        StepVerifier.create(adminService.listModels())
                .assertNext(dto -> {
                    assertEquals("m-1", dto.getId());
                    assertEquals("GPT-4o", dto.getName());
                    assertEquals("OPENAI", dto.getProvider());
                })
                .assertNext(dto -> {
                    assertEquals("m-2", dto.getId());
                    assertEquals("Claude Sonnet", dto.getName());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getModel: Returns model DTO when model exists")
    public void testGetModelSuccess() {
        Model model = Model.builder().id("m-1").name("GPT-4o").provider("OPENAI").modelName("gpt-4o").isActive(true).build();
        when(modelRepository.findById("m-1")).thenReturn(Mono.just(model));

        StepVerifier.create(adminService.getModel("m-1"))
                .assertNext(dto -> assertEquals("GPT-4o", dto.getName()))
                .verifyComplete();
    }

    @Test
    @DisplayName("getModel: Throws NOT_FOUND exception when model does not exist")
    public void testGetModelNotFound() {
        when(modelRepository.findById("invalid-id")).thenReturn(Mono.empty());

        StepVerifier.create(adminService.getModel("invalid-id"))
                .expectErrorMatches(throwable -> throwable instanceof ResponseStatusException rse && rse.getStatusCode().value() == 404)
                .verify();
    }

    @Test
    @DisplayName("createModel: Encrypts credentials and saves new model")
    public void testCreateModel() {
        ModelDto dto = ModelDto.builder()
                .name("New Model")
                .provider("AZURE")
                .modelName("deployment-1")
                .credentials("secret-api-key")
                .build();

        when(cryptoService.encrypt("secret-api-key")).thenReturn("encrypted-secret");
        when(entityTemplate.insert(any(Model.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(adminService.createModel(dto))
                .assertNext(res -> {
                    assertNotNull(res.getId());
                    assertEquals("New Model", res.getName());
                    assertTrue(res.isHasCredentials());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("listGroups: Returns user groups and assigns model IDs")
    public void testListGroups() {
        UserGroup group = UserGroup.builder().id("g-1").groupName("DEVELOPER_GROUP").maxDailyCredits(BigDecimal.valueOf(1000)).build();
        GroupModelAccess access1 = GroupModelAccess.builder().id("a-1").groupId("g-1").modelId("m-1").build();
        GroupModelAccess access2 = GroupModelAccess.builder().id("a-2").groupId("g-1").modelId("m-2").build();

        when(userGroupRepository.findAll()).thenReturn(Flux.just(group));
        when(groupModelAccessRepository.findByGroupId("g-1")).thenReturn(Flux.just(access1, access2));

        StepVerifier.create(adminService.listGroups())
                .assertNext(dto -> {
                    assertEquals("DEVELOPER_GROUP", dto.getGroupName());
                    assertEquals(2, dto.getAllowedModelIds().size());
                    assertTrue(dto.getAllowedModelIds().contains("m-1"));
                    assertTrue(dto.getAllowedModelIds().contains("m-2"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("createUser: Hashes password and inserts new user")
    public void testCreateUser() {
        UserDto dto = UserDto.builder()
                .username("john_doe")
                .email("john@company.com")
                .password("plain-password")
                .groupId("g-1")
                .build();

        when(passwordEncoder.encode("plain-password")).thenReturn("hashed-password");
        when(entityTemplate.insert(any(User.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(userGroupRepository.findById("g-1")).thenReturn(Mono.just(UserGroup.builder().id("g-1").groupName("DEV_GROUP").build()));

        StepVerifier.create(adminService.createUser(dto))
                .assertNext(user -> {
                    assertEquals("john_doe", user.getUsername());
                    assertEquals("john@company.com", user.getEmail());
                    assertEquals("DEV_GROUP", user.getGroupName());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("createUser: Throws BAD_REQUEST when password is empty")
    public void testCreateUserEmptyPassword() {
        UserDto dto = UserDto.builder().username("john_doe").password("").build();

        StepVerifier.create(adminService.createUser(dto))
                .expectErrorMatches(throwable -> throwable instanceof ResponseStatusException rse && rse.getStatusCode().value() == 400)
                .verify();
    }

    @Test
    @DisplayName("upsertCreditRate: Saves custom credit rates for model")
    public void testUpsertCreditRate() {
        CreditRateDto dto = CreditRateDto.builder()
                .modelId("m-1")
                .inputMultiplier(BigDecimal.valueOf(1.5))
                .outputMultiplier(BigDecimal.valueOf(3.0))
                .build();

        when(creditRateRepository.findByModelId("m-1")).thenReturn(Mono.empty());
        when(entityTemplate.insert(any(CreditRate.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(adminService.upsertCreditRate(dto))
                .assertNext(res -> {
                    assertEquals("m-1", res.getModelId());
                    assertEquals(BigDecimal.valueOf(1.5), res.getInputMultiplier());
                    assertEquals(BigDecimal.valueOf(3.0), res.getOutputMultiplier());
                })
                .verifyComplete();
    }
}

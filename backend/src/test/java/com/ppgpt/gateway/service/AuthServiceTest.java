package com.ppgpt.gateway.service;

import com.ppgpt.gateway.domain.User;
import com.ppgpt.gateway.domain.UserGroup;
import com.ppgpt.gateway.dto.LoginRequest;
import com.ppgpt.gateway.repository.UserGroupRepository;
import com.ppgpt.gateway.repository.UserRepository;
import com.ppgpt.gateway.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserGroupRepository userGroupRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private QuotaService quotaService;
    @Mock
    private R2dbcEntityTemplate entityTemplate;

    private AuthService authService;

    @BeforeEach
    public void setUp() {
        authService = new AuthService(
                userRepository,
                userGroupRepository,
                jwtTokenProvider,
                passwordEncoder,
                quotaService,
                entityTemplate
        );
        ReflectionTestUtils.setField(authService, "mockAdEnabled", true);
    }

    @Test
    @DisplayName("loginLocal: Successfully authenticates local user with valid password")
    public void testLoginLocalSuccess() {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("Secret123!");
        req.setAuthSource("LOCAL");

        User user = User.builder()
                .id("u-admin")
                .username("admin")
                .email("admin@company.com")
                .passwordHash("hashed-password")
                .authSource("LOCAL")
                .groupId("g-admin")
                .build();

        UserGroup group = UserGroup.builder()
                .id("g-admin")
                .groupName("ADMIN_GROUP")
                .build();

        when(userRepository.findByUsername("admin")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("Secret123!", "hashed-password")).thenReturn(true);
        when(userGroupRepository.findById("g-admin")).thenReturn(Mono.just(group));
        when(quotaService.getDailyUsage("u-admin")).thenReturn(Mono.just(BigDecimal.ZERO));
        when(jwtTokenProvider.generateToken("u-admin", "admin", "ROLE_ADMIN")).thenReturn("mock-jwt-token");
        when(jwtTokenProvider.getExpiryMs()).thenReturn(86400000L);

        StepVerifier.create(authService.login(req))
                .assertNext(res -> {
                    assertEquals("mock-jwt-token", res.getToken());
                    assertEquals("u-admin", res.getUserId());
                    assertEquals("admin", res.getUsername());
                    assertEquals("ROLE_ADMIN", res.getRole());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("loginLocal: Throws UNAUTHORIZED when password does not match")
    public void testLoginLocalInvalidPassword() {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("WrongPassword");
        req.setAuthSource("LOCAL");

        User user = User.builder()
                .id("u-admin")
                .username("admin")
                .passwordHash("hashed-password")
                .authSource("LOCAL")
                .build();

        when(userRepository.findByUsername("admin")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("WrongPassword", "hashed-password")).thenReturn(false);

        StepVerifier.create(authService.login(req))
                .expectErrorMatches(throwable -> throwable instanceof ResponseStatusException rse && rse.getStatusCode().value() == 401)
                .verify();
    }

    @Test
    @DisplayName("loginAzureAd: Provisions new user JIT when user does not exist in DB")
    public void testLoginAzureAdJitProvisioning() {
        LoginRequest req = new LoginRequest();
        req.setUsername("newuser@company.com");
        req.setPassword("AnyPassword");
        req.setAuthSource("AZURE_AD");

        UserGroup defaultGroup = UserGroup.builder()
                .id("g-default")
                .groupName("DEFAULT_GROUP")
                .build();

        when(userRepository.findByUsername("newuser@company.com")).thenReturn(Mono.empty());
        when(userGroupRepository.findByGroupName("DEFAULT_GROUP")).thenReturn(Mono.just(defaultGroup));
        when(passwordEncoder.encode(any())).thenReturn("hashed-random-pass");
        when(entityTemplate.insert(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(userGroupRepository.findById("g-default")).thenReturn(Mono.just(defaultGroup));
        when(quotaService.getDailyUsage(any())).thenReturn(Mono.just(BigDecimal.ZERO));
        when(jwtTokenProvider.generateToken(any(), eq("newuser@company.com"), eq("ROLE_USER"))).thenReturn("jit-jwt-token");
        when(jwtTokenProvider.getExpiryMs()).thenReturn(86400000L);

        StepVerifier.create(authService.login(req))
                .assertNext(res -> {
                    assertEquals("jit-jwt-token", res.getToken());
                    assertEquals("newuser@company.com", res.getUsername());
                    assertEquals("ROLE_USER", res.getRole());
                })
                .verifyComplete();
    }
}

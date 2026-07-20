package com.ppgpt.gateway.service;

import com.ppgpt.gateway.domain.User;
import com.ppgpt.gateway.dto.AuthResponse;
import com.ppgpt.gateway.dto.LoginRequest;
import com.ppgpt.gateway.repository.UserGroupRepository;
import com.ppgpt.gateway.repository.UserRepository;
import com.ppgpt.gateway.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Authentication service supporting LOCAL and AZURE_AD (mock LDAP) auth.
 *
 * JIT Provisioning: On first AZURE_AD login, a new user record is
 * automatically created and assigned to DEFAULT_GROUP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

        private static final String DEFAULT_GROUP_NAME = "DEFAULT_GROUP";
        private static final String ADMIN_GROUP_NAME = "ADMIN_GROUP";
        private static final String ROLE_ADMIN = "ROLE_ADMIN";
        private static final String ROLE_USER = "ROLE_USER";

        private final UserRepository userRepository;
        private final UserGroupRepository userGroupRepository;
        private final JwtTokenProvider jwtTokenProvider;
        private final PasswordEncoder passwordEncoder;
        private final QuotaService quotaService;
        private final R2dbcEntityTemplate entityTemplate;

        @Value("${app.mock-ad.enabled:true}")
        private boolean mockAdEnabled;

        // ─── Login ───────────────────────────────────────────────────────────────

        public Mono<AuthResponse> login(LoginRequest request) {
                return switch (request.getAuthSource()) {
                        case "LOCAL" -> localLogin(request);
                        case "AZURE_AD" -> azureAdLogin(request);
                        default -> Mono.error(new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "Unknown auth_source: " + request.getAuthSource()));
                };
        }

        public Mono<AuthResponse> getMe(String userId) {
                return buildAuthResponse(userId);
        }

        // ─── LOCAL auth ──────────────────────────────────────────────────────────

        private Mono<AuthResponse> localLogin(LoginRequest request) {
                return userRepository.findByUsername(request.getUsername())
                                .switchIfEmpty(Mono.error(new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "Invalid credentials")))
                                .filter(u -> "LOCAL".equals(u.getAuthSource()))
                                .switchIfEmpty(Mono.error(new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "Account is not a LOCAL account")))
                                .filter(u -> passwordEncoder.matches(request.getPassword(), u.getPasswordHash()))
                                .switchIfEmpty(Mono.error(new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "Invalid credentials")))
                                .flatMap(u -> buildAuthResponse(u.getId()));
        }

        // ─── AZURE_AD (mock) auth ────────────────────────────────────────────────

        /**
         * Mock LDAP bind: accepts any non-empty username/password.
         * In production, replace with real MSAL/OIDC token validation.
         *
         * JIT Provisioning: If the user doesn't exist, create them with DEFAULT_GROUP.
         */
        private Mono<AuthResponse> azureAdLogin(LoginRequest request) {
                if (!mockAdEnabled) {
                        return Mono.error(new ResponseStatusException(
                                        HttpStatus.SERVICE_UNAVAILABLE, "AZURE_AD auth is not enabled"));
                }

                // Simulate AD bind: non-empty credentials = success
                if (request.getPassword() == null || request.getPassword().isBlank()) {
                        return Mono.error(new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED, "AD authentication failed"));
                }

                return userRepository.findByUsername(request.getUsername())
                                .switchIfEmpty(jitProvision(request.getUsername()))
                                .flatMap(u -> buildAuthResponse(u.getId()));
        }

        /**
         * Just-In-Time provisioning: create a new user record for a first-time AD
         * login.
         */
        private Mono<User> jitProvision(String username) {
                log.info("JIT provisioning new AD user: {}", username);
                return userGroupRepository.findByGroupName(DEFAULT_GROUP_NAME)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                "DEFAULT_GROUP not found — seed data missing")))
                                .flatMap(defaultGroup -> {
                                        User newUser = User.builder()
                                                        .id(UUID.randomUUID().toString())
                                                        .username(username)
                                                        .email(username + "@ad.local")
                                                        .authSource("AZURE_AD")
                                                        .groupId(defaultGroup.getId())
                                                        .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                                                        .build();
                                        return entityTemplate.insert(newUser);
                                });
        }

        // ─── Response builder ─────────────────────────────────────────────────────

        private Mono<AuthResponse> buildAuthResponse(String userId) {
                return userRepository.findById(userId)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "User not found")))
                                .flatMap(user -> userGroupRepository.findById(user.getGroupId())
                                                .switchIfEmpty(userGroupRepository.findByGroupName(DEFAULT_GROUP_NAME))
                                                .zipWith(quotaService.getDailyUsage(userId))
                                                .map(tuple -> {
                                                        var group = tuple.getT1();
                                                        var used = tuple.getT2();

                                                        String role = ADMIN_GROUP_NAME.equals(group.getGroupName())
                                                                        ? ROLE_ADMIN
                                                                        : ROLE_USER;

                                                        String token = jwtTokenProvider.generateToken(
                                                                        userId, user.getUsername(), role);

                                                        return AuthResponse.builder()
                                                                        .token(token)
                                                                        .userId(userId)
                                                                        .username(user.getUsername())
                                                                        .email(user.getEmail())
                                                                        .role(role)
                                                                        .groupName(group.getGroupName())
                                                                        .maxDailyCredits(group.getMaxDailyCredits())
                                                                        .creditsUsedToday(used)
                                                                        .expiresAt(System.currentTimeMillis()
                                                                                        + jwtTokenProvider
                                                                                                        .getExpiryMs())
                                                                        .build();
                                                }));
        }
}

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
 * Authentication service supporting LOCAL BCrypt password verification and AZURE_AD (mock LDAP / JIT provisioning).
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

    /**
     * Authenticates a login request (LOCAL or AZURE_AD) and issues a JWT token.
     *
     * @param request Login credentials and auth source
     * @return Mono emitting AuthResponse payload containing JWT token and user info
     */
    public Mono<AuthResponse> login(LoginRequest request) {
        String authSource = request.getAuthSource() != null ? request.getAuthSource().toUpperCase() : "LOCAL";

        if ("AZURE_AD".equals(authSource)) {
            return loginAzureAd(request);
        }
        return loginLocal(request);
    }

    /**
     * Handles local BCrypt password authentication.
     */
    private Mono<AuthResponse> loginLocal(LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password")))
                .flatMap(user -> {
                    if (!"LOCAL".equalsIgnoreCase(user.getAuthSource())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "This account uses " + user.getAuthSource() + " authentication."));
                    }
                    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
                    }
                    return buildAuthResponse(user);
                });
    }

    /**
     * Handles Azure AD (mock LDAP) authentication with Just-In-Time (JIT) user provisioning.
     */
    private Mono<AuthResponse> loginAzureAd(LoginRequest request) {
        if (!mockAdEnabled) {
            return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Azure AD integration is disabled"));
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password required for Azure AD login"));
        }

        String username = request.getUsername().trim().toLowerCase();

        return userRepository.findByUsername(username)
                .flatMap(this::buildAuthResponse)
                .switchIfEmpty(Mono.defer(() -> jitProvisionUser(username)));
    }

    /**
     * Provisions a new user automatically on first Azure AD login.
     */
    private Mono<AuthResponse> jitProvisionUser(String username) {
        log.info("[JIT Provisioning] Creating new Azure AD user: {}", username);

        return userGroupRepository.findByGroupName(DEFAULT_GROUP_NAME)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Default user group '" + DEFAULT_GROUP_NAME + "' not found. Database seeding incomplete.")))
                .flatMap(defaultGroup -> {
                    User newUser = User.builder()
                            .id(UUID.randomUUID().toString())
                            .username(username)
                            .email(username.contains("@") ? username : username + "@company.com")
                            .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .authSource("AZURE_AD")
                            .groupId(defaultGroup.getId())
                            .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                            .build();

                    return entityTemplate.insert(newUser)
                            .flatMap(this::buildAuthResponse);
                });
    }

    /**
     * Builds AuthResponse DTO including JWT token and current usage metrics.
     *
     * @param user Authenticated user entity
     * @return Mono emitting AuthResponse payload
     */
    public Mono<AuthResponse> buildAuthResponse(User user) {
        return userGroupRepository.findById(user.getGroupId())
                .flatMap(group -> quotaService.getDailyUsage(user.getId())
                        .map(creditsUsed -> {
                            String role = ADMIN_GROUP_NAME.equalsIgnoreCase(group.getGroupName())
                                    ? ROLE_ADMIN
                                    : ROLE_USER;

                            String token = jwtTokenProvider.generateToken(
                                    user.getId(),
                                    user.getUsername(),
                                    role
                            );

                            long expiryMs = jwtTokenProvider.getExpiryMs();
                            long expiresAt = System.currentTimeMillis() + expiryMs;

                            return AuthResponse.builder()
                                    .token(token)
                                    .userId(user.getId())
                                    .username(user.getUsername())
                                    .email(user.getEmail())
                                    .role(role)
                                    .groupName(group.getGroupName())
                                    .maxDailyCredits(group.getMaxDailyCredits())
                                    .creditsUsedToday(creditsUsed)
                                    .expiresAt(expiresAt)
                                    .build();
                        }));
    }

    /**
     * Retrieves current user profile and credit usage metrics.
     *
     * @param userId Authenticated user ID
     * @return Mono emitting AuthResponse payload
     */
    public Mono<AuthResponse> getCurrentUser(String userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")))
                .flatMap(this::buildAuthResponse);
    }
}

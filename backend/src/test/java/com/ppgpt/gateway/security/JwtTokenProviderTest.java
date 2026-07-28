package com.ppgpt.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String secretKey = "super-secret-key-that-is-at-least-32-chars-long-for-hmac-sha256";
    private final long expiryMs = 3600000; // 1 hour

    @BeforeEach
    public void setUp() {
        jwtTokenProvider = new JwtTokenProvider(secretKey, expiryMs);
    }

    @Test
    @DisplayName("generateToken: Creates valid JWT token containing claims")
    public void testGenerateTokenAndValidate() {
        String token = jwtTokenProvider.generateToken("user-123", "testuser", "ROLE_ADMIN");
        assertNotNull(token);
        assertTrue(token.length() > 20);

        assertNotNull(jwtTokenProvider.validateAndExtractClaims(token));
        assertEquals("user-123", jwtTokenProvider.extractUserId(token));
        assertEquals("testuser", jwtTokenProvider.extractUsername(token));
        assertEquals("ROLE_ADMIN", jwtTokenProvider.extractRole(token));
    }

    @Test
    @DisplayName("validateAndExtractClaims: Throws Exception for malformed or tampered token")
    public void testInvalidToken() {
        assertThrows(Exception.class, () -> jwtTokenProvider.validateAndExtractClaims("invalid.token.string"));
    }

    @Test
    @DisplayName("validateAndExtractClaims: Throws Exception for expired token")
    public void testExpiredToken() {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(secretKey, -1000); // Expired 1 second ago
        String expiredToken = shortLivedProvider.generateToken("user-123", "testuser", "ROLE_USER");
        assertThrows(Exception.class, () -> shortLivedProvider.validateAndExtractClaims(expiredToken));
    }
}

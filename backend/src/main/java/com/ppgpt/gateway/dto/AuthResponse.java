package com.ppgpt.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String userId;
    private String username;
    private String email;
    private String role;          // "ROLE_USER" | "ROLE_ADMIN"
    private String groupName;
    private BigDecimal maxDailyCredits;
    private BigDecimal creditsUsedToday;
    private long expiresAt;       // Unix epoch millis
}

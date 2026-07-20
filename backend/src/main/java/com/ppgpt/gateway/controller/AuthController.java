package com.ppgpt.gateway.controller;

import com.ppgpt.gateway.dto.AuthResponse;
import com.ppgpt.gateway.dto.LoginRequest;
import com.ppgpt.gateway.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/v1/auth/login
     * Accepts {username, password, authSource}.
     * Returns JWT + quota context.
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request)
                .map(ResponseEntity::ok);
    }

    /**
     * GET /api/v1/auth/me
     * Returns current user info (requires valid JWT).
     */
    @GetMapping("/me")
    public Mono<ResponseEntity<AuthResponse>> me(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return authService.getMe(userId)
                .map(ResponseEntity::ok);
    }
}

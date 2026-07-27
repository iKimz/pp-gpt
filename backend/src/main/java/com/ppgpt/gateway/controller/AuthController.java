package com.ppgpt.gateway.controller;

import com.ppgpt.gateway.dto.AuthResponse;
import com.ppgpt.gateway.dto.LoginRequest;
import com.ppgpt.gateway.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REST Controller for User Authentication (LOCAL and AZURE_AD login, token verification).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Authenticates login credentials and returns JWT bearer token with credit quota details.
     *
     * @param request Login credentials
     * @return Mono emitting AuthResponse payload
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request)
                .map(ResponseEntity::ok);
    }

    /**
     * Retrieves profile and credit usage for currently authenticated user.
     *
     * @param auth Spring Security authentication object
     * @return Mono emitting AuthResponse payload
     */
    @GetMapping("/me")
    public Mono<ResponseEntity<AuthResponse>> me(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return authService.getCurrentUser(userId)
                .map(ResponseEntity::ok);
    }
}

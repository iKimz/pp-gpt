package com.ppgpt.gateway.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactive JWT authentication filter.
 * Extracts "Authorization: Bearer <token>", validates it,
 * and injects the SecurityContext into the reactive chain.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactiveJwtAuthFilter implements WebFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = extractToken(exchange);

        if (token == null || !jwtTokenProvider.isTokenValid(token)) {
            return chain.filter(exchange);
        }

        try {
            Claims claims = jwtTokenProvider.validateAndExtractClaims(token);
            String userId   = claims.getSubject();
            String username = claims.get("username", String.class);
            String role     = claims.get("role", String.class);

            var authorities = List.of(new SimpleGrantedAuthority(role));
            var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            // Store username as detail for controllers
            auth.setDetails(username);

            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
        } catch (Exception e) {
            log.debug("Could not set authentication from JWT: {}", e.getMessage());
            return chain.filter(exchange);
        }
    }

    private String extractToken(ServerWebExchange exchange) {
        String bearerToken = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

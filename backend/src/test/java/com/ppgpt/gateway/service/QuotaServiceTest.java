package com.ppgpt.gateway.service;

import com.ppgpt.gateway.domain.TokenUsage;
import com.ppgpt.gateway.domain.UserGroup;
import com.ppgpt.gateway.repository.TokenUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link QuotaService}.
 */
@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock private ReactiveStringRedisTemplate   redisTemplate;
    @Mock private DefaultRedisScript<Long>      quotaCheckScript;
    @Mock private TokenUsageRepository          tokenUsageRepository;
    @Mock private ReactiveValueOperations<String, String> opsForValue;

    private QuotaService quotaService;

    @BeforeEach
    void setUp() {
        quotaService = new QuotaService(redisTemplate, quotaCheckScript, tokenUsageRepository);
    }

    @Test
    void secondsUntilMidnight_alwaysReturnsAtLeastOne() throws Exception {
        var method = QuotaService.class.getDeclaredMethod("secondsUntilMidnight");
        method.setAccessible(true);
        long ttl = (long) method.invoke(quotaService);
        assertThat(ttl).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void checkAndReserve_redisAllows_returnsTrue() {
        when(redisTemplate.execute(any(), anyList(), anyList()))
                .thenReturn(Flux.just(1L));
        UserGroup group = UserGroup.builder()
                .id("g1").maxDailyCredits(BigDecimal.valueOf(1000)).build();
        StepVerifier.create(quotaService.checkAndReserve("u1", group, BigDecimal.valueOf(10)))
                .expectNext(true).verifyComplete();
    }

    @Test
    void checkAndReserve_redisDenies_returnsFalse() {
        when(redisTemplate.execute(any(), anyList(), anyList()))
                .thenReturn(Flux.just(0L));
        UserGroup group = UserGroup.builder()
                .id("g1").maxDailyCredits(BigDecimal.valueOf(5)).build();
        StepVerifier.create(quotaService.checkAndReserve("u1", group, BigDecimal.valueOf(100)))
                .expectNext(false).verifyComplete();
    }

    @Test
    void checkAndReserve_redisError_fallsBackToDb_andAllows() {
        when(redisTemplate.execute(any(), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("Redis unavailable")));
        TokenUsage usage = new TokenUsage();
        usage.setCreditsUsed(BigDecimal.valueOf(50));
        when(tokenUsageRepository.findByUserIdAndUsageDate(eq("u1"), any(LocalDate.class)))
                .thenReturn(Mono.just(usage));
        UserGroup group = UserGroup.builder()
                .id("g1").maxDailyCredits(BigDecimal.valueOf(1000)).build();
        StepVerifier.create(quotaService.checkAndReserve("u1", group, BigDecimal.valueOf(10)))
                .expectNext(true).verifyComplete();
    }

    @Test
    void getDailyUsage_redisHasValue_returnsIt() {
        when(redisTemplate.opsForValue()).thenReturn(opsForValue);
        when(opsForValue.get(anyString())).thenReturn(Mono.just("123.4567"));
        // Stub DB as safety net (switchIfEmpty is lazy but Mockito needs a non-null Mono)
        when(tokenUsageRepository.findByUserIdAndUsageDate(any(), any()))
                .thenReturn(Mono.empty());
        StepVerifier.create(quotaService.getDailyUsage("u1"))
                .assertNext(val -> assertThat(val).isEqualByComparingTo("123.4567"))
                .verifyComplete();
    }

    @Test
    void getDailyUsage_redisMiss_fallsBackToDb() {
        when(redisTemplate.opsForValue()).thenReturn(opsForValue);
        when(opsForValue.get(anyString())).thenReturn(Mono.empty());
        TokenUsage usage = new TokenUsage();
        usage.setCreditsUsed(BigDecimal.valueOf(77));
        when(tokenUsageRepository.findByUserIdAndUsageDate(eq("u1"), any(LocalDate.class)))
                .thenReturn(Mono.just(usage));
        StepVerifier.create(quotaService.getDailyUsage("u1"))
                .assertNext(val -> assertThat(val).isEqualByComparingTo("77"))
                .verifyComplete();
    }

    @Test
    void getDailyUsage_noDataAnywhere_returnsZero() {
        when(redisTemplate.opsForValue()).thenReturn(opsForValue);
        when(opsForValue.get(anyString())).thenReturn(Mono.empty());
        when(tokenUsageRepository.findByUserIdAndUsageDate(any(), any()))
                .thenReturn(Mono.empty());
        StepVerifier.create(quotaService.getDailyUsage("u1"))
                .expectNext(BigDecimal.ZERO).verifyComplete();
    }
}

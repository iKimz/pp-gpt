package com.ppgpt.gateway.service;

import com.ppgpt.gateway.domain.TokenUsage;
import com.ppgpt.gateway.domain.UserGroup;
import com.ppgpt.gateway.repository.TokenUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Manages daily credit quota using Redis as the fast-path store
 * and MariaDB (token_usage) as the durable source of truth.
 *
 * Key format: quota:user:{userId}:{yyyy-MM-dd}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private static final String KEY_PREFIX = "quota:user:";
    private static final int    SCALE       = 4;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long>    quotaCheckScript;
    private final TokenUsageRepository        tokenUsageRepository;

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Atomically check quota and pre-deduct in Redis.
     * Returns true if allowed, false if quota exceeded.
     *
     * @param userId       the requesting user's ID
     * @param group        the user's group (contains max_daily_credits)
     * @param creditAmount credits to reserve
     */
    public Mono<Boolean> checkAndReserve(String userId, UserGroup group, BigDecimal creditAmount) {
        String key   = buildKey(userId);
        String limit = group.getMaxDailyCredits().toPlainString();
        String amount= creditAmount.setScale(SCALE, RoundingMode.HALF_UP).toPlainString();
        long   ttl   = secondsUntilMidnight();

        return redisTemplate.execute(
                quotaCheckScript,
                List.of(key),
                List.of(limit, amount, String.valueOf(ttl))
        )
        .next()
        .map(result -> result == 1L)
        // Fallback: if Redis is down, check DB directly
        .onErrorResume(ex -> {
            log.warn("Redis quota check failed, falling back to DB: {}", ex.getMessage());
            return checkQuotaFromDb(userId, group, creditAmount);
        });
    }

    /**
     * Deduct final credits after stream completes/cancels.
     * First corrects any estimation error in Redis, then persists to DB.
     *
     * @param userId          user
     * @param estimatedCredits what was pre-reserved
     * @param actualCredits    what was actually used
     */
    public Mono<Void> finalizeDeduction(String userId, BigDecimal estimatedCredits, BigDecimal actualCredits) {
        BigDecimal diff = actualCredits.subtract(estimatedCredits);

        // Adjust Redis (add or subtract the diff from estimation)
        Mono<Void> redisAdjust = Mono.empty();
        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            String key       = buildKey(userId);
            String diffStr   = diff.toPlainString();
            redisAdjust = redisTemplate.opsForValue()
                    .increment(key, Double.parseDouble(diffStr))
                    .then();
        }

        // Persist to DB (upsert today's usage)
        Mono<Void> dbPersist = persistToDb(userId, actualCredits);

        return redisAdjust.then(dbPersist);
    }

    /**
     * Get today's credits used for a user.
     * Tries Redis first, falls back to DB.
     */
    public Mono<BigDecimal> getDailyUsage(String userId) {
        return redisTemplate.opsForValue()
                .get(buildKey(userId))
                .map(val -> new BigDecimal(val).setScale(SCALE, RoundingMode.HALF_UP))
                .onErrorResume(ex -> fetchUsageFromDb(userId))
                .switchIfEmpty(fetchUsageFromDb(userId));
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private String buildKey(String userId) {
        return KEY_PREFIX + userId + ":" + LocalDate.now(ZoneOffset.UTC);
    }

    private long secondsUntilMidnight() {
        long secs = LocalTime.now(ZoneOffset.UTC).until(LocalTime.MIDNIGHT, ChronoUnit.SECONDS);
        // until(MIDNIGHT) is negative after midnight-of-day; adding 86400 normalises to [0, 86400).
        // We clamp to minimum 1 so Redis never receives TTL=0 (which evicts immediately).
        return Math.max(1L, secs + 86400L);
    }

    private Mono<Boolean> checkQuotaFromDb(String userId, UserGroup group, BigDecimal amount) {
        return fetchUsageFromDb(userId)
                .map(used -> used.add(amount).compareTo(group.getMaxDailyCredits()) <= 0);
    }

    private Mono<BigDecimal> fetchUsageFromDb(String userId) {
        return tokenUsageRepository.findByUserIdAndUsageDate(userId, LocalDate.now(ZoneOffset.UTC))
                .map(TokenUsage::getCreditsUsed)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    private Mono<Void> persistToDb(String userId, BigDecimal credits) {
        return tokenUsageRepository.upsertCredits(
                UUID.randomUUID().toString(),
                userId,
                LocalDate.now(ZoneOffset.UTC),
                credits
        ).subscribeOn(Schedulers.boundedElastic());
    }
}

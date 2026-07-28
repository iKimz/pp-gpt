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
 * Manages daily user credit quota using Redis as the high-throughput fast-path store
 * and MariaDB (token_usage table) as the durable source of truth.
 *
 * Key format: quota:user:{userId}:{yyyy-MM-dd}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaService {

    private static final String KEY_PREFIX = "quota:user:";
    private static final int SCALE = 4;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> quotaCheckScript;
    private final TokenUsageRepository tokenUsageRepository;

    /**
     * Atomically check quota and pre-deduct estimated credits in Redis via Lua Script.
     *
     * @param userId       Requesting user ID
     * @param group        User group entity
     * @param creditAmount Credits to reserve
     * @return Mono emitting true if within quota limit, false if limit exceeded
     */
    public Mono<Boolean> checkAndReserve(String userId, UserGroup group, BigDecimal creditAmount) {
        return checkAndReserveQuota(userId, group.getMaxDailyCredits(), creditAmount);
    }

    /**
     * Overloaded helper method to check quota with explicit max daily credit limit.
     *
     * @param userId          Requesting user ID
     * @param maxDailyCredits Max allowed daily credits
     * @param creditAmount    Credits to reserve
     * @return Mono emitting true if allowed, false if limit exceeded
     */
    public Mono<Boolean> checkAndReserveQuota(String userId, BigDecimal maxDailyCredits, BigDecimal creditAmount) {
        String key = buildKey(userId);
        String limit = maxDailyCredits.toPlainString();
        String amount = creditAmount.setScale(SCALE, RoundingMode.HALF_UP).toPlainString();
        long ttl = secondsUntilMidnight();

        return redisTemplate.execute(
                quotaCheckScript,
                List.of(key),
                List.of(limit, amount, String.valueOf(ttl))
        )
                .next()
                .map(result -> result == 1L)
                .onErrorResume(ex -> {
                    log.warn("Redis quota check failed, falling back to DB: {}", ex.getMessage());
                    return checkQuotaFromDb(userId, maxDailyCredits, creditAmount);
                });
    }

    /**
     * Finalizes credit deduction after AI stream completes or cancels.
     * Corrects any pre-flight estimation error in Redis, then asynchronously persists to DB.
     *
     * @param userId           User ID
     * @param estimatedCredits Pre-reserved estimated credits
     * @param actualCredits    Actual calculated credits from input/output tokens
     * @return Mono completing upon storage
     */
    public Mono<Void> finalizeDeduction(String userId, BigDecimal estimatedCredits, BigDecimal actualCredits) {
        BigDecimal diff = actualCredits.subtract(estimatedCredits);

        Mono<Void> redisAdjust = Mono.empty();
        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            String key = buildKey(userId);
            String diffStr = diff.toPlainString();
            redisAdjust = redisTemplate.opsForValue()
                    .increment(key, Double.parseDouble(diffStr))
                    .then();
        }

        Mono<Void> dbPersist = persistToDb(userId, actualCredits);
        return redisAdjust.then(dbPersist);
    }

    /**
     * Gets today's credit usage for a user.
     * Checks Redis first, falling back to MariaDB.
     *
     * @param userId User ID
     * @return Mono emitting today's used credit amount
     */
    public Mono<BigDecimal> getDailyUsage(String userId) {
        return redisTemplate.opsForValue()
                .get(buildKey(userId))
                .map(val -> new BigDecimal(val).setScale(SCALE, RoundingMode.HALF_UP))
                .onErrorResume(ex -> fetchUsageFromDb(userId))
                .switchIfEmpty(fetchUsageFromDb(userId));
    }

    /**
     * Builds Redis key for user daily quota storage.
     *
     * @param userId User ID
     * @return Formatted Redis key string
     */
    private String buildKey(String userId) {
        return KEY_PREFIX + userId + ":" + LocalDate.now(ZoneOffset.UTC);
    }

    /**
     * Calculates remaining seconds until UTC midnight for Redis key TTL expiration.
     *
     * @return Seconds until midnight
     */
    private long secondsUntilMidnight() {
        long secs = LocalTime.now(ZoneOffset.UTC).until(LocalTime.MIDNIGHT, ChronoUnit.SECONDS);
        return Math.max(1L, secs + 86400L);
    }

    /**
     * DB Fallback: Checks daily quota against MariaDB token_usage table.
     *
     * @param userId          User ID
     * @param maxDailyCredits Max daily allowed credits
     * @param amount          Credit amount to check
     * @return Mono emitting true if within limit, false otherwise
     */
    private Mono<Boolean> checkQuotaFromDb(String userId, BigDecimal maxDailyCredits, BigDecimal amount) {
        return fetchUsageFromDb(userId)
                .map(used -> used.add(amount).compareTo(maxDailyCredits) <= 0);
    }

    /**
     * Fetches today's total credit usage from MariaDB.
     *
     * @param userId User ID
     * @return Mono emitting total credits used today
     */
    private Mono<BigDecimal> fetchUsageFromDb(String userId) {
        return tokenUsageRepository.findByUserIdAndUsageDate(userId, LocalDate.now(ZoneOffset.UTC))
                .map(TokenUsage::getCreditsUsed)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    /**
     * Persists atomic credit usage updates to MariaDB database.
     *
     * @param userId  User ID
     * @param credits Credits used
     * @return Mono completing when DB update finishes
     */
    private Mono<Void> persistToDb(String userId, BigDecimal credits) {
        return tokenUsageRepository.upsertCredits(
                UUID.randomUUID().toString(),
                userId,
                LocalDate.now(ZoneOffset.UTC),
                credits
        ).subscribeOn(Schedulers.boundedElastic());
    }
}

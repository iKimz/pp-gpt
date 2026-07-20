package com.ppgpt.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Reactive Redis configuration.
 * Provides ReactiveStringRedisTemplate for quota operations
 * and a Lua script bean for atomic check-and-deduct.
 */
@Configuration
public class RedisConfig {

    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }

    /**
     * Lua script for atomic quota check-and-increment.
     * KEYS[1] = quota key (e.g., "quota:user:{userId}:{date}")
     * ARGV[1] = daily limit (string float)
     * ARGV[2] = amount to deduct (string float)
     * Returns 1 if allowed and deducted, 0 if quota exceeded.
     */
    @Bean
    public DefaultRedisScript<Long> quotaCheckScript() {
        String lua = """
                local key = KEYS[1]
                local limit = tonumber(ARGV[1])
                local amount = tonumber(ARGV[2])
                local ttl = tonumber(ARGV[3])
                local current = tonumber(redis.call('GET', key) or '0')
                if current + amount > limit then
                    return 0
                end
                local newVal = redis.call('INCRBYFLOAT', key, amount)
                if tonumber(newVal) == amount then
                    -- First write — set TTL to end of day
                    redis.call('EXPIRE', key, ttl)
                end
                return 1
                """;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);
        return script;
    }
}

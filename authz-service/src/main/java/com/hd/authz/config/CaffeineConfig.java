package com.hd.authz.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Set;

@Configuration
public class CaffeineConfig {

    @Bean
    public Cache<String, Set<String>> permCache(
            @Value("${authz.cache.caffeine.max-weight}") long maxWeight,
            @Value("${authz.cache.caffeine.expire-after-access-min}") long expireMin) {
        return Caffeine.newBuilder()
                .maximumWeight(maxWeight)
                .weigher((String k, Set<String> v) -> v.size() * 32 + k.length())
                .expireAfterAccess(Duration.ofMinutes(expireMin))
                .recordStats()
                .build();
    }

    @Bean
    public Cache<String, Object> metaCache() {
        return Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(Duration.ofMinutes(60))
                .recordStats()
                .build();
    }
}

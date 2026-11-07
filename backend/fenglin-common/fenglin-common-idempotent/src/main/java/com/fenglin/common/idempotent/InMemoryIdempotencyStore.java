package com.fenglin.common.idempotent;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单实例实现，仅用于本地与 P0 骨架；生产替换为 Redis SET NX EX。
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, Long> expiresAt = new ConcurrentHashMap<>();

    @Override
    public boolean putIfAbsent(String key, Duration ttl) {
        long now = System.nanoTime();
        Long previous = expiresAt.putIfAbsent(key, now + ttl.toNanos());
        if (previous == null) {
            return true;
        }
        if (previous > now) {
            return false;
        }
        return expiresAt.replace(key, previous, now + ttl.toNanos());
    }
}

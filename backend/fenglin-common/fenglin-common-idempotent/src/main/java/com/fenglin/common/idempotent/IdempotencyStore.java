package com.fenglin.common.idempotent;

public interface IdempotencyStore {

    /**
     * @return true 表示首次占用成功；false 表示同一幂等键仍在有效期内
     */
    boolean putIfAbsent(String key, java.time.Duration ttl);
}

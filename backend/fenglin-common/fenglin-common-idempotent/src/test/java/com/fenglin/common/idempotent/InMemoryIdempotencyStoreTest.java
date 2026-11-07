package com.fenglin.common.idempotent;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryIdempotencyStoreTest {

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

    @Test
    void rejectsDuplicateWhileKeyIsActive() {
        assertThat(store.putIfAbsent("order-1", Duration.ofMinutes(5))).isTrue();
        assertThat(store.putIfAbsent("order-1", Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void allowsReuseAfterExpiry() {
        assertThat(store.putIfAbsent("order-2", Duration.ZERO)).isTrue();
        assertThat(store.putIfAbsent("order-2", Duration.ofMinutes(5))).isTrue();
    }
}

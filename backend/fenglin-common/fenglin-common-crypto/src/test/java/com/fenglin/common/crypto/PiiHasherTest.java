package com.fenglin.common.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PiiHasherTest {

    private final PiiHasher hasher = new PiiHasher();
    private final String salt = Base64.getEncoder().encodeToString("tenant-salt-1".getBytes());

    @Test
    void normalizesMainlandMobileNumbers() {
        assertThat(hasher.normalizePhone("138 0000 0000")).isEqualTo("+8613800000000");
        assertThat(hasher.normalizePhone("+8613800000000")).isEqualTo("+8613800000000");
        assertThat(hasher.normalizePhone("8613800000000")).isEqualTo("+8613800000000");
    }

    @Test
    void hashIsStableAndSaltSpecific() {
        String first = hasher.hashHex(hasher.normalizePhone("13800000000"), salt);
        String second = hasher.hashHex("+8613800000000", salt);
        String otherSalt = hasher.hashHex("+8613800000000",
                Base64.getEncoder().encodeToString("tenant-salt-2".getBytes()));

        assertThat(first).isEqualTo(second).hasSize(64);
        assertThat(first).isNotEqualTo(otherSalt);
    }

    @Test
    void normalizesEmail() {
        assertThat(hasher.normalizeEmail("  Foo.Bar@Example.COM ")).isEqualTo("foo.bar@example.com");
    }
}

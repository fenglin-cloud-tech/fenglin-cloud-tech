package com.fenglin.iam.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GrantAuditDetailTest {

    @Test
    void recordsCountsAndOrderInsensitiveHashWithoutPlaintextCodes() {
        List<String> codes = List.of("tenant:iam:role:manage", "hub:audit:read");

        Map<String, Object> detail = GrantAuditDetail.of(2, codes);

        assertThat(detail).containsEntry("before_count", 2).containsEntry("after_count", 2);
        String hash = (String) detail.get("after_codes_hash");
        assertThat(hash).startsWith("sha256:").hasSize(71);
        assertThat(hash).isEqualTo(GrantAuditDetail.codesHash(List.of("hub:audit:read", "tenant:iam:role:manage")));
        assertThat(detail.toString()).doesNotContain("tenant:iam:role:manage").doesNotContain("hub:audit:read");
    }

    @Test
    void emptyCodesHashMatchesEmptyStringDigest() {
        Map<String, Object> detail = GrantAuditDetail.of(3, List.of());

        assertThat(detail).containsEntry("before_count", 3).containsEntry("after_count", 0);
        assertThat(detail.get("after_codes_hash"))
                .isEqualTo("sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void differentCodeSetsProduceDifferentHashes() {
        assertThat(GrantAuditDetail.codesHash(List.of("a")))
                .isNotEqualTo(GrantAuditDetail.codesHash(List.of("a", "b")));
    }
}

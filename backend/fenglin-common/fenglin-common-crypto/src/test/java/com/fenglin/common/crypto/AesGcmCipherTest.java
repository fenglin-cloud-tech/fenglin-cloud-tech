package com.fenglin.common.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmCipherTest {

    private final AesGcmCipher cipher = new AesGcmCipher("0123456789abcdef0123456789abcdef"
            .getBytes(StandardCharsets.UTF_8));

    @Test
    void roundTripWithAad() {
        String encrypted = cipher.encrypt("13800000000", "1:hub_employee:phone");
        assertThat(encrypted).isNotEqualTo("13800000000");
        assertThat(cipher.decrypt(encrypted, "1:hub_employee:phone")).isEqualTo("13800000000");
    }

    @Test
    void differentNonceProducesDifferentCiphertext() {
        String first = cipher.encrypt("same", "aad");
        String second = cipher.encrypt("same", "aad");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectsTamperedCiphertext() {
        String encrypted = cipher.encrypt("secret", "aad");
        char[] chars = encrypted.toCharArray();
        chars[chars.length - 2] = chars[chars.length - 2] == 'A' ? 'B' : 'A';

        assertThatThrownBy(() -> cipher.decrypt(new String(chars), "aad"))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void rejectsAadMismatch() {
        String encrypted = cipher.encrypt("secret", "aad-1");
        assertThatThrownBy(() -> cipher.decrypt(encrypted, "aad-2"))
                .isInstanceOf(CryptoException.class);
    }
}

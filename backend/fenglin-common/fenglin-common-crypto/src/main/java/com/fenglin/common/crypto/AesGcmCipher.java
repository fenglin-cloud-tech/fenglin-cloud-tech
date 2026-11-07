package com.fenglin.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM。密文格式：Base64(nonce(12 bytes) || ciphertext || tag(16 bytes))。
 * AAD 使用资源标识（如 tenantId:table:column），防止密文跨字段搬运。
 */
public final class AesGcmCipher {

    private static final int KEY_LENGTH_BYTES = 32;
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCipher(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("AES-256-GCM 需要 32 字节密钥");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext, String aad) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            if (aad != null) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new CryptoException("加密失败", ex);
        }
    }

    public String decrypt(String encoded, String aad) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encoded);
            if (payload.length <= NONCE_LENGTH_BYTES) {
                throw new CryptoException("密文格式不合法");
            }
            byte[] nonce = new byte[NONCE_LENGTH_BYTES];
            byte[] encrypted = new byte[payload.length - NONCE_LENGTH_BYTES];
            System.arraycopy(payload, 0, nonce, 0, NONCE_LENGTH_BYTES);
            System.arraycopy(payload, NONCE_LENGTH_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            if (aad != null) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (CryptoException ex) {
            throw ex;
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new CryptoException("解密失败：密文被篡改或 AAD 不匹配", ex);
        }
    }
}

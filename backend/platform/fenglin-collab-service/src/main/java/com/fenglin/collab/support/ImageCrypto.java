package com.fenglin.collab.support;

import com.fenglin.common.crypto.KmsKeyProvider;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档图片分块加密（COL-07）：HKDF(dataKey, tenantId:collab_document:image) 派生 32 字节密钥，
 * 每 1 MiB 一块，逐块 AES-256-GCM，AAD 绑定租户、用途与块序号。
 *
 * <p>流式处理：任何时刻内存里只有一块（1 MiB）明文/密文，不把整张图片读进堆。</p>
 *
 * <p>密文格式：magic(4) | version(1) | chunkSize(4) | plainSize(8) | repeat[nonce(12) | ciphertext | tag(16)]。
 * 文件服务只保存这段不透明字节，无法按图片格式识别。</p>
 */
@Component
public class ImageCrypto {

    private static final byte[] MAGIC = {'F', 'L', 'I', 'M'};
    private static final byte VERSION = 1;
    /** 分块大小固定 1 MiB；解密时按头部声明值校验，防止异常输入。 */
    public static final int CHUNK_SIZE = 1024 * 1024;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int MAX_ALLOWED_CHUNK = 8 * 1024 * 1024;

    private final byte[] masterKey;
    private final byte[] tokenKey;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<Long, SecretKeySpec> dataKeyCache = new ConcurrentHashMap<>();

    public ImageCrypto(KmsKeyProvider keyProvider) {
        this.masterKey = keyProvider.currentDataKey();
        this.tokenKey = hkdf(masterKey, "collab_document:image:asset_token", 32);
    }

    public SecretKeySpec dataKey(long tenantId) {
        return dataKeyCache.computeIfAbsent(tenantId,
                id -> new SecretKeySpec(hkdf(masterKey, id + ":collab_document:image", 32), "AES"));
    }

    public byte[] tokenKey() {
        return tokenKey;
    }

    /**
     * 流式加密到输出流；调用方需先知道明文长度（用于写头部与解密端计算块数）。
     */
    public void encrypt(long tenantId, InputStream plain, long plainSize, OutputStream out) throws IOException {
        SecretKeySpec key = dataKey(tenantId);
        out.write(MAGIC);
        out.write(VERSION);
        writeInt(out, CHUNK_SIZE);
        writeLong(out, plainSize);
        byte[] buffer = new byte[Math.min(CHUNK_SIZE, (int) Math.max(1, plainSize))];
        long remaining = plainSize;
        int chunkIndex = 0;
        while (remaining > 0) {
            int length = (int) Math.min((long) buffer.length, remaining);
            int read = readFully(plain, buffer, length);
            if (read <= 0) {
                throw new IOException("图片明文长度与预期不一致");
            }
            byte[] encrypted = encryptChunk(key, tenantId, chunkIndex, buffer, read);
            out.write(encrypted);
            remaining -= read;
            chunkIndex++;
        }
        out.flush();
    }

    /**
     * 流式解密；expectedPlainSize 来自数据库元数据，头部不一致直接失败，避免把错误内容写给客户端。
     */
    public long decrypt(long tenantId, InputStream in, OutputStream out, long expectedPlainSize) throws IOException {
        DataInputStream data = new DataInputStream(in);
        byte[] magic = new byte[4];
        data.readFully(magic);
        if (!Arrays.equals(MAGIC, magic)) {
            throw new IOException("图片密文格式非法");
        }
        int version = data.readUnsignedByte();
        if (version != VERSION) {
            throw new IOException("图片密文版本不支持: " + version);
        }
        int chunkSize = data.readInt();
        if (chunkSize <= 0 || chunkSize > MAX_ALLOWED_CHUNK) {
            throw new IOException("图片密文分块大小非法: " + chunkSize);
        }
        long plainSize = data.readLong();
        if (plainSize < 0 || plainSize != expectedPlainSize) {
            throw new IOException("图片密文长度与元数据不一致");
        }
        SecretKeySpec key = dataKey(tenantId);
        byte[] plainBuffer = new byte[Math.min(chunkSize, (int) Math.max(1, plainSize))];
        int totalChunks = (int) ((plainSize + chunkSize - 1) / chunkSize);
        long written = 0;
        for (int index = 0; index < totalChunks; index++) {
            int length = (int) Math.min((long) chunkSize, plainSize - written);
            byte[] record = new byte[NONCE_LENGTH + length + TAG_LENGTH_BITS / 8];
            data.readFully(record);
            byte[] plain = decryptChunk(key, tenantId, index, record);
            if (plain.length != length) {
                throw new IOException("图片密文块长度非法");
            }
            out.write(plain);
            written += plain.length;
        }
        out.flush();
        return written;
    }

    private byte[] encryptChunk(SecretKeySpec key, long tenantId, int chunkIndex, byte[] plain, int length) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(aad(tenantId, chunkIndex));
            byte[] encrypted = cipher.doFinal(plain, 0, length);
            byte[] record = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, record, 0, nonce.length);
            System.arraycopy(encrypted, 0, record, nonce.length, encrypted.length);
            return record;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("图片加密失败", ex);
        }
    }

    private byte[] decryptChunk(SecretKeySpec key, long tenantId, int chunkIndex, byte[] record) {
        try {
            byte[] nonce = Arrays.copyOfRange(record, 0, NONCE_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(aad(tenantId, chunkIndex));
            return cipher.doFinal(record, NONCE_LENGTH, record.length - NONCE_LENGTH);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("图片解密失败：密文被篡改或密钥不匹配", ex);
        }
    }

    private byte[] aad(long tenantId, int chunkIndex) {
        return (tenantId + ":collab_document:image:" + chunkIndex).getBytes(StandardCharsets.UTF_8);
    }

    private static int readFully(InputStream in, byte[] buffer, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = in.read(buffer, total, length - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    private static void writeInt(OutputStream out, int value) throws IOException {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeLong(OutputStream out, long value) throws IOException {
        for (int shift = 56; shift >= 0; shift -= 8) {
            out.write((int) ((value >>> shift) & 0xFF));
        }
    }

    /** HKDF-SHA256（RFC 5869，salt 为 32 字节全零）。 */
    static byte[] hkdf(byte[] ikm, String info, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream okm = new ByteArrayOutputStream();
            byte[] previous = new byte[0];
            int counter = 1;
            while (okm.size() < length) {
                mac.update(previous);
                mac.update(infoBytes);
                mac.update((byte) counter);
                previous = mac.doFinal();
                okm.writeBytes(previous);
                counter++;
            }
            return Arrays.copyOf(okm.toByteArray(), length);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HKDF 派生失败", ex);
        }
    }
}

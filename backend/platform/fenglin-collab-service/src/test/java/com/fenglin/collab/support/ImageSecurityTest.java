package com.fenglin.collab.support;

import com.fenglin.collab.CollabErrorCode;
import com.fenglin.common.core.BizException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * COL-07 图片安全单测：EXIF 剥离、分块加解密、短期令牌。
 */
class ImageSecurityTest {

    private final ImageProcessor processor = new ImageProcessor();
    private final ImageCrypto crypto = new ImageCrypto(
            () -> "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private final ImageAssetTokenService tokens = new ImageAssetTokenService(crypto);

    @Test
    void stripsJpegExifAndReadsDimensions() throws Exception {
        byte[] exifPayload = "Exif\0\0GPS-LAT-39.9042,116.4074".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        jpeg.write(new byte[]{(byte) 0xFF, (byte) 0xD8});
        jpeg.write(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) ((exifPayload.length + 2) >> 8),
                (byte) (exifPayload.length + 2)});
        jpeg.write(exifPayload);
        jpeg.write(new byte[]{(byte) 0xFF, (byte) 0xC0, 0x00, 0x11, 0x08, 0x00, 0x20, 0x00, 0x30,
                0x03, 0x01, 0x11, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01});
        jpeg.write(new byte[]{(byte) 0xFF, (byte) 0xDA, 0x00, 0x0C, 0x01, 0x01, 0x00, 0x00,
                0x3F, 0x00, 0x08, 0x00, 0x09, 0x3F, 0x00});
        jpeg.write(new byte[]{0x01, 0x02, 0x03, (byte) 0xFF, (byte) 0xD9});
        byte[] source = jpeg.toByteArray();

        ImageProcessor.ProcessedImage image = processor.process(new ByteArrayInputStream(source), source.length);
        try {
            byte[] stripped = Files.readAllBytes(image.file());
            assertThat(new String(stripped, StandardCharsets.ISO_8859_1)).doesNotContain("GPS-LAT");
            assertThat(image.width()).isEqualTo(48);
            assertThat(image.height()).isEqualTo(32);
            assertThat(image.mime()).isEqualTo("image/jpeg");
            assertThat(image.sha256()).hasSize(64);
        } finally {
            Files.deleteIfExists(image.file());
        }
    }

    @Test
    void stripsPngMetadataChunks() throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        writePngChunk(png, "IHDR", new byte[]{0x00, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00, 0x18,
                0x08, 0x02, 0x00, 0x00, 0x00});
        writePngChunk(png, "tEXt", "GPSDATA".getBytes(StandardCharsets.ISO_8859_1));
        writePngChunk(png, "eXIf", "EXIF-GPS".getBytes(StandardCharsets.ISO_8859_1));
        writePngChunk(png, "IEND", new byte[0]);
        byte[] source = png.toByteArray();

        ImageProcessor.ProcessedImage image = processor.process(new ByteArrayInputStream(source), source.length);
        try {
            byte[] stripped = Files.readAllBytes(image.file());
            String content = new String(stripped, StandardCharsets.ISO_8859_1);
            assertThat(content).doesNotContain("GPSDATA").doesNotContain("EXIF-GPS");
            assertThat(image.width()).isEqualTo(32);
            assertThat(image.height()).isEqualTo(24);
            assertThat(image.mime()).isEqualTo("image/png");
        } finally {
            Files.deleteIfExists(image.file());
        }
    }

    @Test
    void rejectsOversizedAndOversizedDimensions() throws Exception {
        assertThatThrownBy(() -> processor.process(new ByteArrayInputStream(new byte[0]),
                ImageProcessor.MAX_BYTES + 1))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(String.valueOf(ImageProcessor.MAX_BYTES));

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        writePngChunk(png, "IHDR", new byte[]{0x00, 0x00, 0x23, 0x28, 0x00, 0x00, 0x23, 0x28,
                0x08, 0x02, 0x00, 0x00, 0x00});
        writePngChunk(png, "IEND", new byte[0]);
        byte[] source = png.toByteArray();
        assertThatThrownBy(() -> processor.process(new ByteArrayInputStream(source), source.length))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(String.valueOf(ImageProcessor.MAX_SIDE));
    }

    @Test
    void chunkedEncryptionRoundTripsWithoutPlaintextInCiphertext() throws Exception {
        byte[] plain = new byte[2 * 1024 * 1024 + 123];
        for (int i = 0; i < plain.length; i++) {
            plain[i] = (byte) (i * 31);
        }
        byte[] marker = "PLAINTEXT-IMAGE-MARKER".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(marker, 0, plain, 0, marker.length);
        ByteArrayOutputStream cipher = new ByteArrayOutputStream();
        crypto.encrypt(7001L, new ByteArrayInputStream(plain), plain.length, cipher);
        byte[] cipherBytes = cipher.toByteArray();

        // 密文以自有头开始，且不包含任何明文标记（MinIO 中无法按图片识别）
        assertThat(new String(cipherBytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("FLIM");
        assertThat(new String(cipherBytes, StandardCharsets.ISO_8859_1))
                .doesNotContain("PLAINTEXT-IMAGE-MARKER");
        assertThat(Arrays.mismatch(plain, cipherBytes)).isNotEqualTo(-1);

        ByteArrayOutputStream decrypted = new ByteArrayOutputStream();
        crypto.decrypt(7001L, new ByteArrayInputStream(cipherBytes), decrypted, plain.length);
        assertThat(decrypted.toByteArray()).isEqualTo(plain);

        // 换租户密钥后必须解不开
        assertThatThrownBy(() -> crypto.decrypt(7002L, new ByteArrayInputStream(cipherBytes),
                new ByteArrayOutputStream(), plain.length))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void assetTokenBindsTenantDocFileSubjectAndExpiry() {
        String token = tokens.issue(7001L, "doc-1", 88L, tokens.userSubject(9L));
        ImageAssetTokenService.AssetToken verified = tokens.verify(token);
        assertThat(verified.tenantId()).isEqualTo(7001L);
        assertThat(verified.docId()).isEqualTo("doc-1");
        assertThat(verified.fileId()).isEqualTo(88L);
        assertThat(verified.subject()).isEqualTo("u9");

        assertThatThrownBy(() -> tokens.verify(token + "x"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode().code())
                .isEqualTo(CollabErrorCode.DOC_NO_PERMISSION.code());

        String expired = tokens.issue(7001L, "doc-1", 88L, "u9", -5);
        assertThatThrownBy(() -> tokens.verify(expired))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过期");
    }

    private static void writePngChunk(ByteArrayOutputStream out, String type, byte[] data) {
        int length = data.length;
        out.write((length >>> 24) & 0xFF);
        out.write((length >>> 16) & 0xFF);
        out.write((length >>> 8) & 0xFF);
        out.write(length & 0xFF);
        out.writeBytes(type.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(data);
        out.writeBytes(new byte[4]);
    }
}

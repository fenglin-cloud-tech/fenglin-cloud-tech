package com.fenglin.collab.support;

import com.fenglin.collab.CollabErrorCode;
import com.fenglin.common.core.BizException;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 图片入库预处理（COL-07）：识别 JPEG/PNG/WebP，流式剥离 EXIF/XMP/文本等元数据，
 * 同时计算尺寸与明文 SHA-256。输出写临时文件，不把整张图读进内存。
 *
 * <p>剥离范围：
 * <ul>
 *   <li>JPEG：APP1（Exif/XMP）、APP13（IPTC）、COM 整段丢弃，保留 SOF 尺寸与像素数据；</li>
 *   <li>PNG：eXIf/tEXt/iTXt/zTXt 块丢弃，保留 IHDR/IDAT/IEND 等必要块；</li>
 *   <li>WebP：EXIF、XMP 块丢弃，并回填 RIFF 长度。</li>
 * </ul>
 * 其他格式一律拒绝，避免把未清洗的字节写入密文对象。</p>
 */
@Component
public class ImageProcessor {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    public static final int MAX_SIDE = 8192;
    public static final long MAX_PIXELS = 40_000_000L;
    private static final int COPY_BUFFER = 8192;
    private static final long MAX_CHUNK = 16L * 1024 * 1024;

    public record ProcessedImage(Path file, long size, int width, int height, String mime, String sha256) {
    }

    public ProcessedImage process(InputStream source, long declaredSize) throws IOException {
        if (declaredSize <= 0) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "图片不能为空");
        }
        if (declaredSize > MAX_BYTES) {
            throw new BizException(CollabErrorCode.PARAM_INVALID,
                    "图片超过 10 MB 上限（当前 " + declaredSize + " 字节，上限 " + MAX_BYTES + " 字节）");
        }
        Path temp = Files.createTempFile("collab-image-", ".bin");
        MessageDigest digest = sha256();
        boolean success = false;
        try {
            Format format;
            Dimensions dimensions;
            try (PushbackInputStream input = new PushbackInputStream(new BufferedInputStream(source), 16);
                 DigestOutputStream out = new DigestOutputStream(
                         new BufferedOutputStream(Files.newOutputStream(temp), COPY_BUFFER), digest)) {
                format = detect(input);
                dimensions = switch (format) {
                    case JPEG -> stripJpeg(input, out);
                    case PNG -> stripPng(input, out);
                    case WEBP -> stripWebp(input, out);
                };
                out.flush();
            }
            if (format == Format.WEBP) {
                patchRiffSize(temp);
            }
            validateDimensions(dimensions);
            long size = Files.size(temp);
            // WebP 需要回填 RIFF 长度，摘要按回填后的最终字节重算，保证与落库明文一致。
            String sha256 = format == Format.WEBP
                    ? sha256Of(temp) : HexFormat.of().formatHex(digest.digest());
            success = true;
            return new ProcessedImage(temp, size, dimensions.width(), dimensions.height(),
                    format.mime, sha256);
        } finally {
            if (!success) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private Format detect(PushbackInputStream input) throws IOException {
        byte[] head = new byte[12];
        int read = readFully(input, head, head.length);
        input.unread(head, 0, Math.max(read, 0));
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return Format.JPEG;
        }
        if (read >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && (head[4] & 0xFF) == 0x0D && (head[5] & 0xFF) == 0x0A
                && (head[6] & 0xFF) == 0x1A && (head[7] & 0xFF) == 0x0A) {
            return Format.PNG;
        }
        if (read >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return Format.WEBP;
        }
        throw new BizException(CollabErrorCode.PARAM_INVALID, "图片仅支持 JPEG/PNG/WebP 格式");
    }

    // ---------- JPEG ----------

    private Dimensions stripJpeg(InputStream input, OutputStream out) throws IOException {
        int soi1 = input.read();
        int soi2 = input.read();
        if (soi1 != 0xFF || soi2 != 0xD8) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "JPEG 文件头非法");
        }
        out.write(0xFF);
        out.write(0xD8);
        Dimensions dimensions = null;
        while (true) {
            int prefix = input.read();
            if (prefix < 0) {
                throw new BizException(CollabErrorCode.PARAM_INVALID, "JPEG 文件不完整");
            }
            if (prefix != 0xFF) {
                continue;
            }
            int marker = input.read();
            while (marker == 0xFF) {
                marker = input.read();
            }
            if (marker < 0) {
                throw new BizException(CollabErrorCode.PARAM_INVALID, "JPEG 文件不完整");
            }
            if (marker == 0xD9) {
                out.write(0xFF);
                out.write(0xD9);
                break;
            }
            if ((marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
                out.write(0xFF);
                out.write(marker);
                continue;
            }
            int high = input.read();
            int low = input.read();
            if (high < 0 || low < 0) {
                throw new BizException(CollabErrorCode.PARAM_INVALID, "JPEG 段长度缺失");
            }
            int length = (high << 8) | low;
            if (length < 2) {
                throw new BizException(CollabErrorCode.PARAM_INVALID, "JPEG 段长度非法");
            }
            int payload = length - 2;
            boolean strip = marker == 0xE1 || marker == 0xED || marker == 0xFE;
            if (marker == 0xDA) {
                out.write(0xFF);
                out.write(0xDA);
                out.write(high);
                out.write(low);
                copy(input, out, payload);
                copyAll(input, out);
                break;
            }
            if (strip) {
                skipFully(input, payload);
                continue;
            }
            out.write(0xFF);
            out.write(marker);
            out.write(high);
            out.write(low);
            if (isStartOfFrame(marker)) {
                byte[] header = new byte[payload];
                readFully(input, header, payload);
                if (payload >= 5) {
                    int height = ((header[1] & 0xFF) << 8) | (header[2] & 0xFF);
                    int width = ((header[3] & 0xFF) << 8) | (header[4] & 0xFF);
                    dimensions = new Dimensions(width, height);
                }
                out.write(header);
            } else {
                copy(input, out, payload);
            }
        }
        if (dimensions == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "JPEG 缺少尺寸信息");
        }
        return dimensions;
    }

    private boolean isStartOfFrame(int marker) {
        return marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
    }

    // ---------- PNG ----------

    private Dimensions stripPng(InputStream input, OutputStream out) throws IOException {
        byte[] signature = new byte[8];
        readFully(input, signature, signature.length);
        out.write(signature);
        Dimensions dimensions = null;
        while (true) {
            byte[] lengthBytes = new byte[4];
            readFully(input, lengthBytes, 4);
            long length = ((long) (lengthBytes[0] & 0xFF) << 24)
                    | ((long) (lengthBytes[1] & 0xFF) << 16)
                    | ((long) (lengthBytes[2] & 0xFF) << 8)
                    | (lengthBytes[3] & 0xFF);
            if (length > MAX_CHUNK) {
                throw new BizException(CollabErrorCode.PARAM_INVALID, "PNG 块长度异常");
            }
            byte[] type = new byte[4];
            readFully(input, type, 4);
            String chunkType = new String(type, java.nio.charset.StandardCharsets.US_ASCII);
            boolean strip = "eXIf".equals(chunkType) || "tEXt".equals(chunkType)
                    || "iTXt".equals(chunkType) || "zTXt".equals(chunkType);
            if (strip) {
                skipFully(input, length);
                skipFully(input, 4);
            } else {
                out.write(lengthBytes);
                out.write(type);
                if ("IHDR".equals(chunkType)) {
                    if (length < 8) {
                        throw new BizException(CollabErrorCode.PARAM_INVALID, "PNG IHDR 非法");
                    }
                    byte[] header = new byte[(int) length];
                    readFully(input, header, header.length);
                    int width = ((header[0] & 0xFF) << 24) | ((header[1] & 0xFF) << 16)
                            | ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
                    int height = ((header[4] & 0xFF) << 24) | ((header[5] & 0xFF) << 16)
                            | ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
                    dimensions = new Dimensions(width, height);
                    out.write(header);
                } else {
                    copy(input, out, (int) length);
                }
                copy(input, out, 4);
            }
            if ("IEND".equals(chunkType)) {
                break;
            }
        }
        if (dimensions == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "PNG 缺少尺寸信息");
        }
        return dimensions;
    }

    // ---------- WebP ----------

    private Dimensions stripWebp(InputStream input, OutputStream out) throws IOException {
        byte[] riff = new byte[12];
        readFully(input, riff, 12);
        out.write(riff);
        Dimensions dimensions = null;
        while (true) {
            int b0 = input.read();
            if (b0 < 0) {
                break;
            }
            byte[] head = new byte[8];
            head[0] = (byte) b0;
            int read = readFully(input, head, 1, 7);
            if (read < 7) {
                throw new BizException(CollabErrorCode.PARAM_INVALID, "WebP 块头不完整");
            }
            String fourcc = new String(head, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long size = (head[4] & 0xFFL) | ((head[5] & 0xFFL) << 8) | ((head[6] & 0xFFL) << 16)
                    | ((head[7] & 0xFFL) << 24);
            if (size > MAX_CHUNK) {
                throw new BizException(CollabErrorCode.PARAM_INVALID, "WebP 块长度异常");
            }
            boolean padding = (size & 1) == 1;
            if ("EXIF".equals(fourcc) || "XMP ".equals(fourcc)) {
                skipFully(input, size + (padding ? 1 : 0));
                continue;
            }
            out.write(head);
            if ("VP8X".equals(fourcc) && size >= 10) {
                byte[] data = new byte[10];
                readFully(input, data, 10);
                int width = 1 + ((data[4] & 0xFF) | ((data[5] & 0xFF) << 8) | ((data[6] & 0xFF) << 16));
                int height = 1 + ((data[7] & 0xFF) | ((data[8] & 0xFF) << 8) | ((data[9] & 0xFF) << 16));
                dimensions = new Dimensions(width, height);
                out.write(data);
                copy(input, out, (int) size - 10);
                if (padding) {
                    copy(input, out, 1);
                }
            } else if ("VP8L".equals(fourcc) && size >= 5) {
                byte[] data = new byte[5];
                readFully(input, data, 5);
                if ((data[0] & 0xFF) == 0x2F) {
                    int b1 = data[1] & 0xFF;
                    int b2 = data[2] & 0xFF;
                    int b3 = data[3] & 0xFF;
                    int b4 = data[4] & 0xFF;
                    int width = 1 + (((b2 & 0x3F) << 8) | b1);
                    int height = 1 + (((b4 & 0x0F) << 10) | (b3 << 2) | ((b2 & 0xC0) >> 6));
                    dimensions = new Dimensions(width, height);
                }
                out.write(data);
                copy(input, out, (int) size - 5);
                if (padding) {
                    copy(input, out, 1);
                }
            } else if ("VP8 ".equals(fourcc) && size >= 10) {
                byte[] data = new byte[10];
                readFully(input, data, 10);
                if ((data[3] & 0xFF) == 0x9D && (data[4] & 0xFF) == 0x01 && (data[5] & 0xFF) == 0x2A) {
                    int width = ((data[6] & 0xFF) | ((data[7] & 0xFF) << 8)) & 0x3FFF;
                    int height = ((data[8] & 0xFF) | ((data[9] & 0xFF) << 8)) & 0x3FFF;
                    dimensions = new Dimensions(width, height);
                }
                out.write(data);
                copy(input, out, (int) size - 10);
                if (padding) {
                    copy(input, out, 1);
                }
            } else {
                copy(input, out, (int) size);
                if (padding) {
                    copy(input, out, 1);
                }
            }
        }
        if (dimensions == null) {
            throw new BizException(CollabErrorCode.PARAM_INVALID, "WebP 缺少尺寸信息");
        }
        return dimensions;
    }

    private void patchRiffSize(Path temp) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(temp.toFile(), "rw")) {
            long size = file.length() - 8;
            file.seek(4);
            file.write((int) (size & 0xFF));
            file.write((int) ((size >>> 8) & 0xFF));
            file.write((int) ((size >>> 16) & 0xFF));
            file.write((int) ((size >>> 24) & 0xFF));
        }
    }

    private void validateDimensions(Dimensions dimensions) {
        long pixels = (long) dimensions.width() * dimensions.height();
        if (dimensions.width() <= 0 || dimensions.height() <= 0
                || dimensions.width() > MAX_SIDE || dimensions.height() > MAX_SIDE || pixels > MAX_PIXELS) {
            throw new BizException(CollabErrorCode.PARAM_INVALID,
                    "图片尺寸超限：宽 " + dimensions.width() + "px、高 " + dimensions.height()
                            + "px；单边上限 " + MAX_SIDE + "px，总像素上限 " + MAX_PIXELS);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", ex);
        }
    }

    private static String sha256Of(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[COPY_BUFFER];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static int readFully(InputStream input, byte[] buffer, int length) throws IOException {
        return readFully(input, buffer, 0, length);
    }

    private static int readFully(InputStream input, byte[] buffer, int offset, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = input.read(buffer, offset + total, length - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        if (total < length) {
            throw new EOFException("图片数据不完整");
        }
        return total;
    }

    private static void copy(InputStream input, OutputStream out, int length) throws IOException {
        byte[] buffer = new byte[Math.min(COPY_BUFFER, Math.max(length, 1))];
        int remaining = length;
        while (remaining > 0) {
            int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new EOFException("图片数据不完整");
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void copyAll(InputStream input, OutputStream out) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
    }

    private static void skipFully(InputStream input, long length) throws IOException {
        long remaining = length;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() < 0) {
                    throw new EOFException("图片数据不完整");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private enum Format {
        JPEG("image/jpeg"), PNG("image/png"), WEBP("image/webp");

        private final String mime;

        Format(String mime) {
            this.mime = mime;
        }
    }

    private record Dimensions(int width, int height) {
    }
}

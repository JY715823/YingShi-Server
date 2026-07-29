package com.yingshi.server.service.upload;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 文件魔数校验：通过读取文件头字节判断真实类型，防止上传伪造扩展名的恶意文件。
 * R1-H-1: 阻断任意文件上传。
 */
public class MagicBytesDetector {

    private static final int HEADER_SIZE = 32;

    public enum FileType {
        JPEG(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/jpeg"),
        PNG(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, "image/png"),
        GIF87a("GIF87a".getBytes(StandardCharsets.US_ASCII), "image/gif"),
        GIF89a("GIF89a".getBytes(StandardCharsets.US_ASCII), "image/gif"),
        WEBP("RIFF".getBytes(StandardCharsets.US_ASCII), "image/webp"),
        BMP("BM".getBytes(StandardCharsets.US_ASCII), "image/bmp"),
        MP4(new byte[]{0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70}, "video/mp4"),
        MP4_ALT(new byte[]{0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70}, "video/mp4"),
        QUICKTIME(new byte[]{0x00, 0x00, 0x00, 0x14, 0x66, 0x74, 0x79, 0x70}, "video/quicktime"),
        UNKNOWN(new byte[0], null);

        private final byte[] magic;
        private final String contentType;

        FileType(byte[] magic, String contentType) {
            this.magic = magic;
            this.contentType = contentType;
        }

        public String contentType() {
            return contentType;
        }
    }

    public static FileType detect(byte[] header) {
        if (header == null || header.length == 0) {
            return FileType.UNKNOWN;
        }
        for (FileType type : FileType.values()) {
            if (type == FileType.UNKNOWN) {
                continue;
            }
            if (startsWith(header, type.magic)) {
                if (type == FileType.WEBP) {
                    if (header.length >= 12
                            && header[8] == 'W' && header[9] == 'E'
                            && header[10] == 'B' && header[11] == 'P') {
                        return FileType.WEBP;
                    }
                    continue;
                }
                return type;
            }
        }
        if (header.length >= 8
                && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
            return FileType.MP4;
        }
        return FileType.UNKNOWN;
    }

    private static boolean startsWith(byte[] header, byte[] magic) {
        if (header.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (header[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    public static FileType detect(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[HEADER_SIZE];
            int read = is.read(header);
            if (read <= 0) {
                return FileType.UNKNOWN;
            }
            byte[] trimmed = new byte[read];
            System.arraycopy(header, 0, trimmed, 0, read);
            return detect(trimmed);
        }
    }

    public static boolean isAcceptable(FileType type, String declaredMediaType) {
        if (type == FileType.UNKNOWN) {
            return false;
        }
        String detected = type.contentType();
        if (detected == null || declaredMediaType == null) {
            return false;
        }
        String declared = declaredMediaType.toLowerCase(Locale.ROOT);
        boolean declaredImage = declared.startsWith("image/");
        boolean declaredVideo = declared.startsWith("video/");
        boolean detectedImage = detected.startsWith("image/");
        boolean detectedVideo = detected.startsWith("video/");
        return (declaredImage && detectedImage) || (declaredVideo && detectedVideo);
    }
}

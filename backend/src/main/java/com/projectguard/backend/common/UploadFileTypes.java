package com.projectguard.backend.common;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * 올린 파일의 실제 형식을 앞부분 바이트(시그니처)로 판별한다. 확장자나 브라우저가 알려준 Content-Type은
 * 얼마든지 꾸밀 수 있어서 믿지 않는다. PDF와 JPG만 받는다(운영 정책).
 */
public final class UploadFileTypes {

    public static final String ALLOWED_DESCRIPTION = "PDF 또는 JPG";

    private UploadFileTypes() {
    }

    /** 허용 형식이면 그 형식의 표준 Content-Type, 아니면 빈 값. */
    public static Optional<String> detect(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return Optional.empty();
        }
        if (startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return Optional.of("application/pdf");
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of("image/jpeg");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
    }
}

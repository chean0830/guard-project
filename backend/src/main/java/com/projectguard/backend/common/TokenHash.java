package com.projectguard.backend.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 로그인 세션 토큰은 DB에 원문 대신 SHA-256 해시로 저장한다 — DB가 유출돼도 그 값으로 남의 세션에 들어갈 수 없게.
 * 토큰이 추측할 수 없는 무작위 값이라 소금(salt) 없이 한 번 해시하는 것으로 충분하다.
 */
public final class TokenHash {

    private TokenHash() {
    }

    public static String of(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

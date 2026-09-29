package com.projectguard.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 로그인 세션을 나타내는 불투명 토큰. JWT 대신 랜덤 토큰 + DB 조회 방식을 택해서 서버 쪽에서
 * 즉시 무효화(로그아웃)할 수 있게 했다 — 이 포트폴리오 범위에서는 JWT의 무상태성보다
 * "로그아웃하면 바로 끊긴다"는 단순함이 더 낫다고 판단함 (refresh 토큰 로테이션은 범위 밖).
 */
@Entity
@Table(name = "auth_tokens")
public class AuthToken {

    @Id
    private String token;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Instant expiresAt;

    protected AuthToken() {
    }

    public AuthToken(String token, Long userId, Instant expiresAt) {
        this.token = token;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

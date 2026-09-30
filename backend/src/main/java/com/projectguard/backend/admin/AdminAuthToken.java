package com.projectguard.backend.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 관리자 로그인 세션 토큰. 회원(auth_tokens)·변호사(lawyer_auth_tokens) 토큰과 별도 테이블로 두어
 * 세 계정 체계가 서로의 토큰으로 권한을 얻을 수 없게 한다.
 */
@Entity
@Table(name = "admin_auth_tokens")
public class AdminAuthToken {

    @Id
    private String token;

    @Column(nullable = false)
    private Instant expiresAt;

    protected AdminAuthToken() {
    }

    public AdminAuthToken(String token, Instant expiresAt) {
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

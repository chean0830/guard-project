package com.projectguard.backend.lawyer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 변호사 로그인 세션 토큰. 일반 회원(auth.AuthToken)과 완전히 별도 테이블로 관리해서,
 * 두 계정 체계가 서로 다른 토큰으로 로그인하고 섞이지 않게 한다.
 */
@Entity
@Table(name = "lawyer_auth_tokens")
public class LawyerAuthToken {

    @Id
    private String token;

    @Column(nullable = false)
    private Long lawyerId;

    @Column(nullable = false)
    private Instant expiresAt;

    protected LawyerAuthToken() {
    }

    public LawyerAuthToken(String token, Long lawyerId, Instant expiresAt) {
        this.token = token;
        this.lawyerId = lawyerId;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public Long getLawyerId() {
        return lawyerId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

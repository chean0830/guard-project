package com.projectguard.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 비밀번호 재설정 링크의 토큰. 원문은 메일로만 나가고 DB에는 SHA-256 해시만 둔다 — DB가 새어도
 * 재설정 링크를 만들어낼 수 없게. 회원(USER)과 변호사(LAWYER) 계정을 accountType으로 구분한다.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    /** "USER" | "LAWYER" */
    @Column(nullable = false)
    private String accountType;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    protected PasswordResetToken() {
    }

    public PasswordResetToken(String tokenHash, String accountType, Long accountId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.accountType = accountType;
        this.accountId = accountId;
        this.expiresAt = expiresAt;
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    public boolean isUsable() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }

    public String getAccountType() {
        return accountType;
    }

    public Long getAccountId() {
        return accountId;
    }
}

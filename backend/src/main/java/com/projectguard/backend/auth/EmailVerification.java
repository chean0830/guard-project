package com.projectguard.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 회원가입 이메일 인증. 인증번호와 인증 완료 후 발급하는 가입용 토큰은 원문 대신 해시만 저장한다.
 * 인증번호는 10분 동안 5번까지 입력할 수 있고, 가입용 토큰은 인증 후 30분 안에 한 번만 쓸 수 있다.
 */
@Entity
@Table(name = "email_verifications")
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private Instant codeExpiresAt;

    @Column(nullable = false)
    private int failedAttempts = 0;

    private Instant verifiedAt;

    @Column(unique = true)
    private String tokenHash;

    private Instant usedAt;

    protected EmailVerification() {
    }

    public EmailVerification(String email, String codeHash, Instant codeExpiresAt) {
        this.email = email;
        this.codeHash = codeHash;
        this.codeExpiresAt = codeExpiresAt;
    }

    public void recordFailure() {
        this.failedAttempts++;
    }

    public void markVerified(String tokenHash) {
        this.verifiedAt = Instant.now();
        this.tokenHash = tokenHash;
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getCodeExpiresAt() {
        return codeExpiresAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}

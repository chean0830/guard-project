package com.projectguard.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 앱 소셜 로그인의 1회용 교환 코드. 웹이 소셜 로그인을 끝내고 앱 주소(projectguard://)로 돌려보낼 때
 * 세션 토큰 대신 이 코드를 실어 보낸다 — 다른 앱이 같은 주소를 가로채도, 앱이 처음에 만든 비밀값(PKCE verifier)이
 * 없으면 토큰으로 바꿀 수 없다. 코드 원문은 DB에 두지 않고 SHA-256 해시만 둔다.
 */
@Entity
@Table(name = "app_login_codes")
public class AppLoginCode {

    @Id
    @Column(length = 64)
    private String codeHash;

    /** base64url(SHA-256(verifier)) */
    @Column(nullable = false, length = 64)
    private String codeChallenge;

    @Column(nullable = false)
    private String sessionToken;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Instant expiresAt;

    protected AppLoginCode() {
    }

    public AppLoginCode(String codeHash, String codeChallenge, String sessionToken, String email, Instant expiresAt) {
        this.codeHash = codeHash;
        this.codeChallenge = codeChallenge;
        this.sessionToken = sessionToken;
        this.email = email;
        this.expiresAt = expiresAt;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public String getCodeChallenge() {
        return codeChallenge;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public String getEmail() {
        return email;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

package com.projectguard.backend.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 변호사 상담 기능을 쓰려면 로그인해야 해서 만든 최소한의 회원 정보.
 * 비밀번호는 절대 평문 저장하지 않고 BCrypt로 해시해서 저장한다 (docs/기획서.md 7번 원칙).
 * 소셜 로그인(구글/카카오/네이버)으로 가입한 회원은 비밀번호가 없다 — passwordHash가 null이면
 * 그 계정은 항상 소셜 로그인으로만 들어와야 한다는 뜻이다.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String passwordHash;

    /** "LOCAL"(이메일 가입) | "GOOGLE" | "KAKAO" | "NAVER" */
    @Column(nullable = false)
    private String provider;

    /** 소셜 로그인 제공자가 주는 고유 사용자 ID. LOCAL 계정은 null. */
    private String providerId;

    /** 변호사와의 상담 화면 등에서 이메일 대신 표시할 이름. 선택 입력이라 null일 수 있다. */
    private String name;

    protected User() {
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.provider = "LOCAL";
    }

    public User(String email, String provider, String providerId) {
        this.email = email;
        this.provider = provider;
        this.providerId = providerId;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getName() {
        return name;
    }
}

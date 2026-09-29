package com.projectguard.backend.lawyer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 변호사 회원. 일반 회원(auth.User)과 달리 가입 즉시 로그인할 수 없고, 자격 증명 서류
 * (LawyerCredentialDocument)를 제출한 뒤 관리자가 승인(APPROVED)해야 로그인이 열린다.
 * 아무나 "변호사입니다"라고 주장하며 가입해 상담 기능을 악용하는 것을 막기 위함.
 */
@Entity
@Table(name = "lawyers")
public class Lawyer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    private String lawFirm;

    @Column(nullable = false)
    private String barNumber;

    private String specialties;

    @Column(length = 1000)
    private String introduction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LawyerStatus status = LawyerStatus.PENDING;

    private String rejectionReason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant reviewedAt;

    @Column(nullable = false)
    private boolean emailNotificationsEnabled = true;

    protected Lawyer() {
    }

    public Lawyer(String email, String passwordHash, String name, String lawFirm, String barNumber) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.lawFirm = lawFirm;
        this.barNumber = barNumber;
    }

    public void updateProfile(String name, String lawFirm, String specialties, String introduction) {
        this.name = name;
        this.lawFirm = lawFirm;
        this.specialties = specialties;
        this.introduction = introduction;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setEmailNotificationsEnabled(boolean enabled) {
        this.emailNotificationsEnabled = enabled;
    }

    public void approve() {
        this.status = LawyerStatus.APPROVED;
        this.rejectionReason = null;
        this.reviewedAt = Instant.now();
    }

    public void reject(String reason) {
        this.status = LawyerStatus.REJECTED;
        this.rejectionReason = reason;
        this.reviewedAt = Instant.now();
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

    public String getName() {
        return name;
    }

    public String getLawFirm() {
        return lawFirm;
    }

    public String getBarNumber() {
        return barNumber;
    }

    public String getSpecialties() {
        return specialties;
    }

    public String getIntroduction() {
        return introduction;
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public LawyerStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}

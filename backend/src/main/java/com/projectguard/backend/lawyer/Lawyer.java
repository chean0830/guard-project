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

    /*
     * 회원이 변호사 목록에서 보고 고를 수 있도록 변호사가 직접 적는 강점. 관리자가 검증한 값이 아니라
     * 변호사 본인이 작성한 정보라는 점을 목록 화면에 함께 표시한다.
     */

    /** 한 줄 강점 (예: "전세보증금 반환 소송 다수 승소") */
    @Column(length = 100)
    private String headline;

    /** 변호사 경력 연차 */
    private Integer careerYears;

    /** 수임료 안내 (예: "첫 상담 무료, 착수금 100만원부터") */
    @Column(length = 300)
    private String feeInfo;

    /** 주요 실적. 줄바꿈으로 여러 항목을 적는다. */
    @Column(length = 1000)
    private String achievements;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LawyerStatus status = LawyerStatus.PENDING;

    private String rejectionReason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant reviewedAt;

    @Column(nullable = false)
    private boolean emailNotificationsEnabled = true;

    /**
     * 신고 처리 결과 관리자가 이용을 정지한 변호사. 승인 상태(status)와는 별개로 둔다 — 정지를
     * 풀었을 때 다시 심사할 필요 없이 원래 승인 상태로 돌아가야 하기 때문.
     */
    @Column(nullable = false)
    private boolean blocked = false;

    private String blockedReason;

    private Instant blockedAt;

    /** 연속 비밀번호 오류 횟수. 성공하거나 비밀번호를 재설정하면 0으로 돌아간다. */
    // 기존 행이 있는 DB에 컬럼을 추가해도 실패하지 않도록 기본값을 DB에도 둔다.
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int failedLoginCount = 0;

    /** 비밀번호를 5회 연속 틀려 잠긴 상태. 비밀번호 찾기(이메일 인증)로만 풀린다. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean loginLocked = false;

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

    public void updateStrengths(String headline, Integer careerYears, String feeInfo, String achievements) {
        this.headline = headline;
        this.careerYears = careerYears;
        this.feeInfo = feeInfo;
        this.achievements = achievements;
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

    /** 비밀번호 오류를 기록하고, 한도에 도달하면 잠근다. 잠겼으면 true. */
    public boolean recordLoginFailure(int maxAttempts) {
        this.failedLoginCount++;
        if (this.failedLoginCount >= maxAttempts) {
            this.loginLocked = true;
        }
        return this.loginLocked;
    }

    public void resetLoginFailures() {
        this.failedLoginCount = 0;
        this.loginLocked = false;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public boolean isLoginLocked() {
        return loginLocked;
    }

    public void block(String reason) {
        this.blocked = true;
        this.blockedReason = reason;
        this.blockedAt = Instant.now();
    }

    public void unblock() {
        this.blocked = false;
        this.blockedReason = null;
        this.blockedAt = null;
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

    public String getHeadline() {
        return headline;
    }

    public Integer getCareerYears() {
        return careerYears;
    }

    public String getFeeInfo() {
        return feeInfo;
    }

    public String getAchievements() {
        return achievements;
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

    public boolean isBlocked() {
        return blocked;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public Instant getBlockedAt() {
        return blockedAt;
    }
}

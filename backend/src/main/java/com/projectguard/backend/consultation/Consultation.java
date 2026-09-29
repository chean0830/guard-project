package com.projectguard.backend.consultation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 회원 한 명과 변호사 한 명 사이의 문의 대화방. 회원-변호사 실제 엔티티와는 ID로만 연결한다
 * (auth.AuthToken/lawyer.LawyerAuthToken이 그렇듯, 이 프로젝트 전반의 패턴).
 * 회원이 상담을 신청할 때마다 승인된 변호사 중 무작위로 매칭해 새로 만든다(기존 데모 UX와 동일).
 */
@Entity
@Table(name = "consultations")
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long lawyerId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant lastMessageAt = Instant.now();

    protected Consultation() {
    }

    public Consultation(Long userId, Long lawyerId) {
        this.userId = userId;
        this.lawyerId = lawyerId;
    }

    public void touch() {
        this.lastMessageAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getLawyerId() {
        return lawyerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }
}

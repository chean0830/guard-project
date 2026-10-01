package com.projectguard.backend.consultation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 회원·변호사가 상대방을 직접 차단한 기록. 관리자의 이용 정지(User/Lawyer.blocked)와는 별개로,
 * 당사자끼리만 효력이 있다 — 차단 관계가 있는 두 사람은 서로 메시지를 주고받을 수 없고 새 상담으로
 * 매칭되지도 않는다. 차단은 항상 회원 한 명과 변호사 한 명 사이에 생기므로 두 ID를 고정 칸에 둔다.
 */
@Entity
@Table(name = "chat_blocks", uniqueConstraints = @UniqueConstraint(columnNames = {"blockerType", "userId", "lawyerId"}))
public class ChatBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 차단한 쪽. USER면 회원이 변호사를, LAWYER면 변호사가 회원을 차단한 것. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    private SenderType blockerType;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long lawyerId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ChatBlock() {
    }

    public ChatBlock(SenderType blockerType, Long userId, Long lawyerId) {
        this.blockerType = blockerType;
        this.userId = userId;
        this.lawyerId = lawyerId;
    }

    public Long getId() {
        return id;
    }

    public SenderType getBlockerType() {
        return blockerType;
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
}

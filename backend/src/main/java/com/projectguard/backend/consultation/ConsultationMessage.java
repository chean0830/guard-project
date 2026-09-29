package com.projectguard.backend.consultation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "consultation_messages")
public class ConsultationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long consultationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SenderType senderType;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** 보낸 쪽의 반대편이 아직 읽지 않았으면 false. 목록 화면의 안 읽음 표시에 쓴다. */
    @Column(nullable = false)
    private boolean read = false;

    protected ConsultationMessage() {
    }

    public ConsultationMessage(Long consultationId, SenderType senderType, String content) {
        this.consultationId = consultationId;
        this.senderType = senderType;
        this.content = content;
    }

    public void markRead() {
        this.read = true;
    }

    public Long getId() {
        return id;
    }

    public Long getConsultationId() {
        return consultationId;
    }

    public SenderType getSenderType() {
        return senderType;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }
}

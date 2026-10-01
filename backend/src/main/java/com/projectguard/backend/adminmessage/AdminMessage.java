package com.projectguard.backend.adminmessage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** 관리자가 변호사 한 명에게 보낸 1:1 메시지. 변호사는 읽기만 하고 답장하지 않는다(단방향 알림함). */
@Entity
@Table(name = "admin_messages", indexes = @Index(columnList = "lawyerId"))
public class AdminMessage {

    public static final int MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long lawyerId;

    @Column(nullable = false, length = MAX_LENGTH)
    private String content;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** 변호사가 알림함을 열어 읽은 시각. null이면 안 읽음. */
    private Instant readAt;

    protected AdminMessage() {
    }

    public AdminMessage(Long lawyerId, String content) {
        this.lawyerId = lawyerId;
        this.content = content;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getLawyerId() {
        return lawyerId;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}

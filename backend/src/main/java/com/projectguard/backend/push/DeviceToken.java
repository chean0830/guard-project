package com.projectguard.backend.push;

import com.projectguard.backend.consultation.SenderType;
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
 * 모바일 앱(FCM) 푸시 토큰. 회원·변호사 모두 앱에서 로그인하면 기기 토큰을 등록한다.
 * 토큰은 기기마다 고유해서, 같은 기기에서 다른 계정으로 로그인하면 소유자만 바꾼다(upsert).
 */
@Entity
@Table(name = "device_push_tokens")
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SenderType ownerType;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @Column(length = 20)
    private String platform;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected DeviceToken() {
    }

    public DeviceToken(SenderType ownerType, Long ownerId, String token, String platform) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.token = token;
        this.platform = platform;
    }

    public void assignTo(SenderType ownerType, Long ownerId, String platform) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.platform = platform;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public SenderType getOwnerType() {
        return ownerType;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getToken() {
        return token;
    }

    public String getPlatform() {
        return platform;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

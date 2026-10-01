package com.projectguard.backend.push;

import com.projectguard.backend.consultation.SenderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    List<DeviceToken> findByOwnerTypeAndOwnerId(SenderType ownerType, Long ownerId);

    Optional<DeviceToken> findByToken(String token);

    @Transactional
    void deleteByToken(String token);
}

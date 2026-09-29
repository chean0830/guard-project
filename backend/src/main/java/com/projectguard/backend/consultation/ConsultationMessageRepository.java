package com.projectguard.backend.consultation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsultationMessageRepository extends JpaRepository<ConsultationMessage, Long> {
    List<ConsultationMessage> findByConsultationIdOrderByCreatedAtAsc(Long consultationId);

    Optional<ConsultationMessage> findTopByConsultationIdOrderByCreatedAtDesc(Long consultationId);

    long countByConsultationIdAndSenderTypeAndReadFalse(Long consultationId, SenderType senderType);
}

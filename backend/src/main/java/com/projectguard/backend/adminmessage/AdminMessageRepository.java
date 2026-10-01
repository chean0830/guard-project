package com.projectguard.backend.adminmessage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminMessageRepository extends JpaRepository<AdminMessage, Long> {
    List<AdminMessage> findByLawyerIdOrderByCreatedAtDesc(Long lawyerId);

    List<AdminMessage> findByLawyerIdAndReadAtIsNull(Long lawyerId);

    long countByLawyerIdAndReadAtIsNull(Long lawyerId);
}

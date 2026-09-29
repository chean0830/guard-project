package com.projectguard.backend.lawyer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LawyerCredentialDocumentRepository extends JpaRepository<LawyerCredentialDocument, Long> {
    List<LawyerCredentialDocument> findByLawyerId(Long lawyerId);
}

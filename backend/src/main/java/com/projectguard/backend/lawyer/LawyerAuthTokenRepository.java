package com.projectguard.backend.lawyer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface LawyerAuthTokenRepository extends JpaRepository<LawyerAuthToken, String> {
    @Transactional
    void deleteByLawyerId(Long lawyerId);
}

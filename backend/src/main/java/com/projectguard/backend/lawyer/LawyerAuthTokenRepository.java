package com.projectguard.backend.lawyer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LawyerAuthTokenRepository extends JpaRepository<LawyerAuthToken, String> {
}

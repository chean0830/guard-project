package com.projectguard.backend.admin;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuthTokenRepository extends JpaRepository<AdminAuthToken, String> {
}

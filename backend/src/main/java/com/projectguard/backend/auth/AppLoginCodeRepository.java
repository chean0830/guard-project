package com.projectguard.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppLoginCodeRepository extends JpaRepository<AppLoginCode, String> {
}

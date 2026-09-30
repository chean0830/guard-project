package com.projectguard.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, String> {
    /** 비밀번호를 재설정하면 기존 로그인 세션을 모두 끊는다. */
    @Transactional
    void deleteByUserId(Long userId);
}

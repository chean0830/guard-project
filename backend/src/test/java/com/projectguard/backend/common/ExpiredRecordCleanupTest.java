package com.projectguard.backend.common;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.AuthToken;
import com.projectguard.backend.auth.AuthTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 만료 기록 정리: 지워야 할 것만 지우고, 아이디당 무료 분석 같은 누적 횟수는 절대 지우지 않는다. */
@SpringBootTest
@Transactional
class ExpiredRecordCleanupTest {

    @Autowired
    private ExpiredRecordCleanup cleanup;

    @Autowired
    private AuthService authService;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private RateLimitCounterRepository counterRepository;

    @Test
    void 만료된_세션만_지우고_살아있는_세션은_남긴다() {
        String live = authService.signup("cleanup-" + UUID.randomUUID() + "@example.com", "password123").token();
        Long userId = authService.validate(live).orElseThrow().getId();
        String expired = "expired-" + UUID.randomUUID();
        authTokenRepository.save(new AuthToken(expired, userId, Instant.now().minusSeconds(60)));

        cleanup.cleanup(Instant.now());

        assertFalse(authTokenRepository.existsById(expired));
        assertTrue(authService.validate(live).isPresent());
    }

    @Test
    void 지난_날짜의_하루_횟수는_지우지만_아이디당_누적_횟수는_남긴다() {
        String email = "lifetime-" + UUID.randomUUID() + "@example.com";
        rateLimitService.tryConsumeLifetime("analyze-free", email, 5);
        RateLimitCounter old = counterRepository.save(new RateLimitCounter("old-" + UUID.randomUUID(), LocalDate.now().minusDays(10)));
        rateLimitService.tryConsume("today-scope", email, 5);

        cleanup.cleanup(Instant.now());

        assertFalse(counterRepository.existsById(old.getId()));
        assertEquals(1, rateLimitService.usedLifetime("analyze-free", email));
        assertEquals(1, rateLimitService.usedToday("today-scope", email));
    }
}

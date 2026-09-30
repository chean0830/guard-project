package com.projectguard.backend.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 매일 새벽 4시(한국 시간)에 더 이상 쓸 수 없는 기록을 지운다. 로그인할 때마다 세션 토큰이 생기고,
 * 비밀번호 찾기·가입 인증·횟수 제한도 기록을 남기는데 지우는 곳이 없어 DB가 계속 커지기 때문.
 *
 * 주의: 아이디당 무료 분석 횟수처럼 기간 없이 누적하는 횟수(RateLimitService.LIFETIME 날짜 줄)는
 * 절대 지우지 않는다 — 지우면 무료 횟수가 다시 생긴다.
 */
@Component
public class ExpiredRecordCleanup {

    private static final Logger log = LoggerFactory.getLogger(ExpiredRecordCleanup.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 쓰였거나 만료된 인증 기록도 문의 대응을 위해 하루는 남겨 둔다. */
    private static final Duration GRACE = Duration.ofDays(1);

    @PersistenceContext
    private EntityManager em;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void scheduledCleanup() {
        int removed = cleanup(Instant.now());
        log.info("만료 기록 정리: {}건 삭제", removed);
    }

    /** 기준 시각(now)으로 정리하고 지운 건수를 돌려준다. 테스트에서 시각을 바꿔 호출할 수 있게 분리. */
    @Transactional
    public int cleanup(Instant now) {
        Instant graceCutoff = now.minus(GRACE);
        LocalDate yesterday = LocalDate.ofInstant(now, KST).minusDays(1);
        int removed = 0;

        removed += delete("delete from AuthToken t where t.expiresAt < :now", "now", now);
        removed += delete("delete from LawyerAuthToken t where t.expiresAt < :now", "now", now);
        removed += delete("delete from AdminAuthToken t where t.expiresAt < :now", "now", now);
        removed += delete("delete from PasswordResetToken t where t.expiresAt < :cutoff", "cutoff", graceCutoff);
        removed += delete("delete from EmailVerification v where v.codeExpiresAt < :cutoff", "cutoff", graceCutoff);
        removed += em.createQuery("delete from RateLimitCounter c where c.day < :yesterday and c.day <> :lifetime")
                .setParameter("yesterday", yesterday)
                .setParameter("lifetime", RateLimitService.LIFETIME)
                .executeUpdate();
        return removed;
    }

    private int delete(String jpql, String param, Instant value) {
        return em.createQuery(jpql).setParameter(param, value).executeUpdate();
    }
}

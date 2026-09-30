package com.projectguard.backend.common;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;

/**
 * 횟수 제한. 하루 단위(한국 시간 자정 기준, 예: 비밀번호 찾기 메일)와 기간 없는 누적(예: 아이디당 무료 분석)을
 * 둘 다 지원한다. 이메일 등 원문은 저장하지 않고 SHA-256 해시로만 센다.
 */
@Service
public class RateLimitService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 기간 제한 없는(평생) 횟수는 이 고정 날짜 한 줄에 쌓는다. */
    static final LocalDate LIFETIME = LocalDate.of(2000, 1, 1);

    private final RateLimitCounterRepository repository;

    public RateLimitService(RateLimitCounterRepository repository) {
        this.repository = repository;
    }

    /** 오늘 사용 횟수가 limit 미만이면 1 늘리고 true, 이미 다 썼으면 false. */
    @Transactional
    public boolean tryConsume(String scope, String rawKey, int limit) {
        String key = keyOf(scope, rawKey);
        LocalDate today = LocalDate.now(KST);
        RateLimitCounter counter = repository.findByCounterKeyAndDay(key, today)
                .orElseGet(() -> repository.save(new RateLimitCounter(key, today)));
        if (counter.getCount() >= limit) {
            return false;
        }
        counter.increment();
        repository.save(counter);
        return true;
    }

    /** 기간 제한 없이 누적 횟수가 limit 미만이면 1 늘리고 true. */
    @Transactional
    public boolean tryConsumeLifetime(String scope, String rawKey, int limit) {
        String key = keyOf(scope, rawKey);
        RateLimitCounter counter = repository.findByCounterKeyAndDay(key, LIFETIME)
                .orElseGet(() -> repository.save(new RateLimitCounter(key, LIFETIME)));
        if (counter.getCount() >= limit) {
            return false;
        }
        counter.increment();
        repository.save(counter);
        return true;
    }

    public int usedLifetime(String scope, String rawKey) {
        return repository.findByCounterKeyAndDay(keyOf(scope, rawKey), LIFETIME)
                .map(RateLimitCounter::getCount)
                .orElse(0);
    }

    public int usedToday(String scope, String rawKey) {
        return repository.findByCounterKeyAndDay(keyOf(scope, rawKey), LocalDate.now(KST))
                .map(RateLimitCounter::getCount)
                .orElse(0);
    }

    private static String keyOf(String scope, String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((rawKey == null ? "" : rawKey.trim().toLowerCase()).getBytes(StandardCharsets.UTF_8));
            return scope + ":" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

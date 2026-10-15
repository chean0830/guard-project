package com.projectguard.backend.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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

    private final TransactionTemplate transaction;
    private final TransactionTemplate newTransaction;

    public RateLimitService(RateLimitCounterRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 오늘 사용 횟수가 limit 미만이면 1 늘리고 true, 이미 다 썼으면 false. */
    public boolean tryConsume(String scope, String rawKey, int limit) {
        return consume(keyOf(scope, rawKey), LocalDate.now(KST), limit);
    }

    /** 기간 제한 없이 누적 횟수가 limit 미만이면 1 늘리고 true. */
    public boolean tryConsumeLifetime(String scope, String rawKey, int limit) {
        return consume(keyOf(scope, rawKey), LIFETIME, limit);
    }

    private boolean consume(String key, LocalDate day, int limit) {
        ensureCounter(key, day);
        // 줄을 잠그고 읽으므로 동시에 온 요청은 여기서 한 줄로 서서 차례대로 센다.
        return Boolean.TRUE.equals(transaction.execute(status -> {
            RateLimitCounter counter = repository.findByCounterKeyAndDay(key, day).orElseThrow();
            if (counter.getCount() >= limit) {
                return false;
            }
            counter.increment();
            repository.save(counter);
            return true;
        }));
    }

    /**
     * 횟수 줄이 없으면 먼저 만들어 둔다. 처음 쓰는 키로 요청이 동시에 오면 둘 다 줄을 만들려다 하나가
     * 중복 오류(500)로 실패했다 — 줄 만들기만 별도 트랜잭션으로 떼어, 늦은 쪽은 오류를 넘기고 이미 만들어진 줄을 쓴다.
     * 메서드 전체를 @Transactional로 묶지 않는 이유: 바깥 트랜잭션이 DB 연결을 쥔 채 새 트랜잭션용 연결을 또 기다리면,
     * 요청이 몰릴 때 연결 풀이 바닥나 서로 기다리다 멈춘다(동시 요청 테스트로 확인).
     */
    private void ensureCounter(String key, LocalDate day) {
        if (repository.existsByCounterKeyAndDay(key, day)) {
            return;
        }
        try {
            newTransaction.executeWithoutResult(status -> repository.saveAndFlush(new RateLimitCounter(key, day)));
        } catch (DataIntegrityViolationException e) {
            // 동시에 온 다른 요청이 먼저 만들었다.
        }
    }

    /** 기간 없는 누적 횟수 1회를 돌려준다 (예: 무료 분석이 실패했을 때). */
    public void refundLifetime(String scope, String rawKey) {
        String key = keyOf(scope, rawKey);
        transaction.executeWithoutResult(status -> repository.findByCounterKeyAndDay(key, LIFETIME).ifPresent(counter -> {
            counter.decrement();
            repository.save(counter);
        }));
    }

    public int usedLifetime(String scope, String rawKey) {
        return used(keyOf(scope, rawKey), LIFETIME);
    }

    public int usedToday(String scope, String rawKey) {
        return used(keyOf(scope, rawKey), LocalDate.now(KST));
    }

    /** 줄을 잠그는 조회라 트랜잭션 안에서만 읽을 수 있다. */
    private int used(String key, LocalDate day) {
        Integer count = transaction.execute(status ->
                repository.findByCounterKeyAndDay(key, day).map(RateLimitCounter::getCount).orElse(0));
        return count == null ? 0 : count;
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

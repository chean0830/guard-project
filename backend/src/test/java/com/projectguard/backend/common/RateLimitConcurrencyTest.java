package com.projectguard.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 같은 계정으로 동시에 여러 요청이 와도 횟수 제한을 넘기거나 오류가 나지 않는다. */
@SpringBootTest
class RateLimitConcurrencyTest {

    @Autowired
    private RateLimitService rateLimitService;

    @Test
    void 처음_쓰는_키로_동시에_요청해도_제한만큼만_통과하고_오류가_없다() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.com";
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return rateLimitService.tryConsumeLifetime("race-test", email, 5);
            }));
        }
        start.countDown();

        int passed = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                passed++;
            }
        }
        pool.shutdown();

        assertEquals(5, passed);
        assertEquals(5, rateLimitService.usedLifetime("race-test", email));
    }
}

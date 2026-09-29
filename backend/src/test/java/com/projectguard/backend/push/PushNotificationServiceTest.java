package com.projectguard.backend.push;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 테스트 환경에는 VAPID 키가 설정되어 있지 않으므로(의도적으로), notifyUser가 예외 없이
 * 조용히 아무 일도 하지 않는지 확인한다 — 실제 발송 성공/실패 자체는 외부 푸시 서비스에
 * 의존하는 부분이라 단위 테스트 범위 밖이며, 이 서비스가 상담 흐름을 절대 깨지 않는다는
 * 계약만 검증한다.
 */
@SpringBootTest
@Transactional
class PushNotificationServiceTest {

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private PushSubscriptionRepository subscriptionRepository;

    @Test
    void VAPID_키가_없으면_구독이_있어도_예외없이_조용히_건너뛴다() {
        subscriptionRepository.save(new PushSubscription(1L, "https://example.com/endpoint-1", "p256dh-key", "auth-key"));

        assertDoesNotThrow(() -> pushNotificationService.notifyUser(1L, 99L, "새 답변이 도착했어요"));
    }

    @Test
    void 구독이_없는_회원에게_알려도_예외가_나지_않는다() {
        assertDoesNotThrow(() -> pushNotificationService.notifyUser(999L, 1L, "새 답변이 도착했어요"));
    }
}

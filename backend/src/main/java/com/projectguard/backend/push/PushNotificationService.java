package com.projectguard.backend.push;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.security.GeneralSecurityException;
import java.security.Security;

/**
 * 변호사가 답장을 보내면 회원의 브라우저로 웹 푸시 알림을 보낸다. VAPID 키가 없거나
 * 발송이 실패해도(구독 만료 등) 상담 자체는 항상 성공해야 하므로, 예외를 절대 밖으로
 * 던지지 않는다 — ConsultationMailService와 같은 원칙.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final PushSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;
    private final PushService pushService;

    public PushNotificationService(
            PushSubscriptionRepository subscriptionRepository,
            ObjectMapper objectMapper,
            @Value("${push.vapid.public-key:}") String publicKey,
            @Value("${push.vapid.private-key:}") String privateKey,
            @Value("${push.vapid.subject:}") String subject
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.objectMapper = objectMapper;
        this.pushService = buildPushService(publicKey, privateKey, subject);
    }

    private PushService buildPushService(String publicKey, String privateKey, String subject) {
        if (publicKey.isBlank() || privateKey.isBlank()) {
            log.warn("웹 푸시 설정(VAPID_PUBLIC_KEY/VAPID_PRIVATE_KEY)이 없어 푸시 알림 기능이 비활성화됩니다.");
            return null;
        }
        try {
            return new PushService(publicKey, privateKey, subject);
        } catch (GeneralSecurityException e) {
            log.warn("VAPID 키 설정이 올바르지 않아 푸시 알림 기능이 비활성화됩니다: {}", e.getMessage());
            return null;
        }
    }

    public record PushPayload(String title, String body, String url) {
    }

    public void notifyUser(Long userId, Long consultationId, String previewText) {
        if (pushService == null) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(
                    new PushPayload("Project Guard", previewText, "/consult/" + consultationId)
            );
        } catch (Exception e) {
            log.warn("푸시 알림 페이로드 생성에 실패했습니다: {}", e.getMessage());
            return;
        }

        for (PushSubscription subscription : subscriptionRepository.findByUserId(userId)) {
            sendOne(subscription, payload);
        }
    }

    private void sendOne(PushSubscription subscription, String payload) {
        try {
            Subscription target = new Subscription(
                    subscription.getEndpoint(),
                    new Subscription.Keys(subscription.getP256dh(), subscription.getAuth())
            );
            HttpResponse response = pushService.send(new Notification(target, payload));
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                subscriptionRepository.deleteByEndpoint(subscription.getEndpoint());
            }
        } catch (Exception e) {
            log.warn("웹 푸시 발송에 실패했습니다. endpoint: {}, 사유: {}", subscription.getEndpoint(), e.getMessage());
        }
    }
}

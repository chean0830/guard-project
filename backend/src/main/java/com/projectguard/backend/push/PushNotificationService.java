package com.projectguard.backend.push;

import com.projectguard.backend.consultation.SenderType;
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
import java.util.Map;

/**
 * 상담 새 메시지 푸시 알림. 회원에게는 브라우저(웹 푸시)와 모바일 앱(FCM) 양쪽으로, 변호사에게는 앱(FCM)으로
 * 보낸다(변호사의 웹 알림은 이메일). VAPID·FCM 키가 없거나
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
    private final DeviceTokenRepository deviceTokenRepository;
    private final FcmClient fcmClient;

    public PushNotificationService(
            PushSubscriptionRepository subscriptionRepository,
            DeviceTokenRepository deviceTokenRepository,
            FcmClient fcmClient,
            ObjectMapper objectMapper,
            @Value("${push.vapid.public-key:}") String publicKey,
            @Value("${push.vapid.private-key:}") String privateKey,
            @Value("${push.vapid.subject:}") String subject
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.fcmClient = fcmClient;
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

    /** 변호사가 보낸 메시지를 회원에게 알린다. senderName은 앱 대화방 제목(변호사 이름)으로 쓴다. */
    public void notifyUser(Long userId, Long consultationId, String previewText, String senderName) {
        sendToApp(SenderType.USER, userId, consultationId, previewText, senderName + " 변호사");
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

    /** 회원이 보낸 문의·메시지를 변호사 앱에 알린다. senderName은 회원 표시 이름. */
    public void notifyLawyer(Long lawyerId, Long consultationId, String previewText, String senderName) {
        sendToApp(SenderType.LAWYER, lawyerId, consultationId, previewText, senderName);
    }

    /** 관리자가 보낸 1:1 메시지를 변호사 앱에 알린다. 알림을 누르면 관리자 메시지 알림함이 열린다. */
    public void notifyLawyerAdminMessage(Long lawyerId, String content) {
        sendToApp(SenderType.LAWYER, lawyerId, "관리자 메시지", content, Map.of(
                "type", "ADMIN_MESSAGE",
                "recipientType", SenderType.LAWYER.name()
        ));
    }

    private void sendToApp(SenderType ownerType, Long ownerId, Long consultationId, String previewText, String senderName) {
        sendToApp(ownerType, ownerId, senderName, previewText, Map.of(
                "type", "CONSULTATION_MESSAGE",
                "consultationId", String.valueOf(consultationId),
                "counterpartName", senderName,
                "recipientType", ownerType.name()
        ));
    }

    private void sendToApp(SenderType ownerType, Long ownerId, String title, String text, Map<String, String> data) {
        if (!fcmClient.isEnabled()) {
            return;
        }
        String body = text.length() > 120 ? text.substring(0, 120) + "…" : text;
        for (DeviceToken device : deviceTokenRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)) {
            if (fcmClient.send(device.getToken(), title, body, data) == FcmClient.Result.INVALID_TOKEN) {
                deviceTokenRepository.deleteByToken(device.getToken());
            }
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

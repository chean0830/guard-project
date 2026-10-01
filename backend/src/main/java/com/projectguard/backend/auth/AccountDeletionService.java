package com.projectguard.backend.auth;

import com.projectguard.backend.consultation.ChatBlockRepository;
import com.projectguard.backend.consultation.Consultation;
import com.projectguard.backend.consultation.ConsultationMessageRepository;
import com.projectguard.backend.consultation.ConsultationRepository;
import com.projectguard.backend.consultation.SenderType;
import com.projectguard.backend.payment.PaymentOrderRepository;
import com.projectguard.backend.payment.PaymentStatus;
import com.projectguard.backend.push.DeviceTokenRepository;
import com.projectguard.backend.push.PushSubscriptionRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴. 개인정보처리방침대로 계정과 그 회원의 상담 대화·차단·알림 구독·로그인 세션을 지운다.
 * 결제 기록만은 전자상거래법상 5년 보관 의무가 있어 남긴다(회원 ID만 남고 계정 정보는 사라짐).
 * 쓰지 않은 이용권이나 검토 중인 환불이 있으면 돈이 걸린 일이라 먼저 정리하도록 탈퇴를 막는다.
 */
@Service
public class AccountDeletionService {

    public static final String SOCIAL_CONFIRM_TEXT = "탈퇴";

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final ConsultationRepository consultationRepository;
    private final ConsultationMessageRepository messageRepository;
    private final ChatBlockRepository chatBlockRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AccountDeletionService(
            UserRepository userRepository,
            AuthTokenRepository authTokenRepository,
            PasswordResetTokenRepository resetTokenRepository,
            ConsultationRepository consultationRepository,
            ConsultationMessageRepository messageRepository,
            ChatBlockRepository chatBlockRepository,
            PushSubscriptionRepository pushSubscriptionRepository,
            DeviceTokenRepository deviceTokenRepository,
            PaymentOrderRepository paymentOrderRepository
    ) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.consultationRepository = consultationRepository;
        this.messageRepository = messageRepository;
        this.chatBlockRepository = chatBlockRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.paymentOrderRepository = paymentOrderRepository;
    }

    /** 이메일 가입 회원은 비밀번호, 소셜 전용 회원은 확인 문구("탈퇴")로 본인 의사를 확인한다. */
    @Transactional
    public void withdraw(Long userId, String password, String confirmText) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
        if (user.getPasswordHash() != null) {
            if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new InvalidCredentialsException("비밀번호가 올바르지 않습니다.");
            }
        } else if (!SOCIAL_CONFIRM_TEXT.equals(confirmText == null ? null : confirmText.trim())) {
            throw new IllegalArgumentException("확인을 위해 '" + SOCIAL_CONFIRM_TEXT + "'를 정확히 입력해주세요.");
        }

        boolean moneyPending = paymentOrderRepository.findByUserIdAndStatusNotOrderByCreatedAtDesc(userId, PaymentStatus.READY)
                .stream()
                .anyMatch(o -> o.getStatus() == PaymentStatus.REFUND_REQUESTED
                        || (o.getStatus() == PaymentStatus.PAID && !o.isUsed()));
        if (moneyPending) {
            throw new IllegalStateException("사용하지 않은 이용권이나 검토 중인 환불이 있어요. 결제 내역에서 먼저 결제 취소하거나 환불 결과를 받은 뒤 탈퇴해주세요.");
        }

        for (Consultation consultation : consultationRepository.findByUserIdOrderByLastMessageAtDesc(userId)) {
            messageRepository.deleteAll(messageRepository.findByConsultationIdOrderByCreatedAtAsc(consultation.getId()));
            consultationRepository.delete(consultation);
        }
        chatBlockRepository.deleteAll(chatBlockRepository.findByUserId(userId));
        pushSubscriptionRepository.deleteAll(pushSubscriptionRepository.findByUserId(userId));
        deviceTokenRepository.deleteAll(deviceTokenRepository.findByOwnerTypeAndOwnerId(SenderType.USER, userId));
        resetTokenRepository.deleteByAccountTypeAndAccountId("USER", userId);
        authTokenRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }
}

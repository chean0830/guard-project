package com.projectguard.backend.api;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.InvalidCredentialsException;
import com.projectguard.backend.auth.User;
import com.projectguard.backend.common.RateLimitService;
import com.projectguard.backend.payment.PaymentService;
import com.projectguard.backend.payment.ProductType;
import org.springframework.stereotype.Service;

/**
 * 등기부 분석 이용 규칙(사용자 요구사항): 로그인한 아이디마다 무료 5회, 그 뒤로는 990원 분석 이용권을 1장씩 쓴다.
 * 분석마다 유료 OCR(Google Vision)과 공공 API를 호출하므로 로그인 없이는 쓸 수 없다.
 * 무료 횟수는 회원 ID가 아니라 이메일(해시) 기준으로 센다 — 탈퇴 후 같은 이메일로 다시 가입해도 무료 5회가 다시 생기지 않게.
 */
@Service
public class AnalysisAccessService {

    public static final int FREE_ANALYSES_PER_ACCOUNT = 5;
    private static final String SCOPE = "analyze-free";
    /** 한 계정이 하루에 시도할 수 있는 분석 횟수 (무료·결제, 성공·실패 모두 센다). */
    public static final int ATTEMPTS_PER_DAY = 20;
    private static final String ATTEMPT_SCOPE = "analyze-attempt";

    private final RateLimitService rateLimitService;
    private final PaymentService paymentService;
    private final AuthService authService;

    public AnalysisAccessService(RateLimitService rateLimitService, PaymentService paymentService, AuthService authService) {
        this.rateLimitService = rateLimitService;
        this.paymentService = paymentService;
        this.authService = authService;
    }

    /**
     * 무료 사용(paid=false) 또는 결제한 이용권 사용(paid=true, creditOrderId는 미리 잡아둔 이용권의 주문번호).
     * email은 분석이 실패했을 때 무료 횟수를 돌려줄 계정.
     */
    public record Access(boolean paid, Long userId, String creditOrderId, String email) {
    }

    /**
     * 분석 전에 호출한다. 무료 횟수가 남아 있으면 그것부터 쓰고, 다 썼을 때만 결제한 이용권을 쓴다.
     * 무료 횟수·이용권 모두 여기서 미리 차감하고(동시에 보낸 요청이 1회분으로 여러 번 분석하지 못하게),
     * 분석이 실패하면 release로 돌려준다.
     */
    public Access authorize(String sessionToken) {
        User user = authService.validate(sessionToken)
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
        boolean hasFree = freeRemaining(user.getEmail()) > 0;
        if (!hasFree && paymentService.availableCredits(user.getId(), ProductType.ANALYSIS) == 0) {
            throw new AnalysisPaymentRequiredException();
        }
        // 실패한 분석은 횟수를 돌려주므로, 일부러 실패하는 파일을 반복해 유료 OCR만 쓰는 것을 하루 시도 횟수로 막는다.
        if (!rateLimitService.tryConsume(ATTEMPT_SCOPE, user.getEmail(), ATTEMPTS_PER_DAY)) {
            throw new AnalysisAttemptLimitException();
        }
        if (rateLimitService.tryConsumeLifetime(SCOPE, user.getEmail(), FREE_ANALYSES_PER_ACCOUNT)) {
            return new Access(false, user.getId(), null, user.getEmail());
        }
        String creditOrderId = paymentService.reserveAnalysisCredit(user.getId());
        if (creditOrderId == null) {
            throw new AnalysisPaymentRequiredException();
        }
        return new Access(true, user.getId(), creditOrderId, user.getEmail());
    }

    /** 분석이 실패했을 때 호출한다. 미리 차감한 무료 횟수 또는 결제 이용권을 돌려준다. */
    public void release(Access access) {
        if (access.paid()) {
            paymentService.releaseAnalysisCredit(access.creditOrderId());
        } else {
            rateLimitService.refundLifetime(SCOPE, access.email());
        }
    }

    public int freeRemaining(String email) {
        return Math.max(0, FREE_ANALYSES_PER_ACCOUNT - rateLimitService.usedLifetime(SCOPE, email));
    }
}

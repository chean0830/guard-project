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

    private final RateLimitService rateLimitService;
    private final PaymentService paymentService;
    private final AuthService authService;

    public AnalysisAccessService(RateLimitService rateLimitService, PaymentService paymentService, AuthService authService) {
        this.rateLimitService = rateLimitService;
        this.paymentService = paymentService;
        this.authService = authService;
    }

    /** 무료 사용(paid=false) 또는 결제한 이용권 사용(paid=true). */
    public record Access(boolean paid, Long userId) {
    }

    /** 분석 전에 호출한다. 무료 횟수가 남아 있으면 그것부터 쓰고, 다 썼을 때만 결제한 이용권을 쓴다. */
    public Access authorize(String sessionToken) {
        User user = authService.validate(sessionToken)
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
        if (rateLimitService.tryConsumeLifetime(SCOPE, user.getEmail(), FREE_ANALYSES_PER_ACCOUNT)) {
            return new Access(false, user.getId());
        }
        if (paymentService.availableCredits(user.getId(), ProductType.ANALYSIS) > 0) {
            return new Access(true, user.getId());
        }
        throw new AnalysisPaymentRequiredException();
    }

    /** 분석이 성공한 뒤 호출한다. 결제 이용권을 쓴 경우에만 차감한다. */
    public void complete(Access access) {
        if (access.paid()) {
            paymentService.consumeAnalysisCredit(access.userId());
        }
    }

    public int freeRemaining(String email) {
        return Math.max(0, FREE_ANALYSES_PER_ACCOUNT - rateLimitService.usedLifetime(SCOPE, email));
    }
}

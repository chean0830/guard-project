package com.projectguard.backend.push;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.InvalidCredentialsException;
import com.projectguard.backend.auth.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원이 브라우저에서 웹 푸시 알림을 구독/해지하는 API. 구독은 회원 세션(auth.AuthService)
 * 토큰으로 인증된 사용자에게 귀속된다.
 */
@RestController
@RequestMapping("/api/push")
public class PushSubscriptionController {

    private final PushSubscriptionRepository subscriptionRepository;
    private final AuthService authService;
    private final String vapidPublicKey;

    public PushSubscriptionController(
            PushSubscriptionRepository subscriptionRepository,
            AuthService authService,
            @Value("${push.vapid.public-key:}") String vapidPublicKey
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.authService = authService;
        this.vapidPublicKey = vapidPublicKey;
    }

    public record VapidPublicKeyResponse(String publicKey) {
    }

    public record SubscriptionKeys(String p256dh, String auth) {
    }

    public record SubscribeRequest(String endpoint, SubscriptionKeys keys) {
    }

    public record UnsubscribeRequest(String endpoint) {
    }

    @GetMapping("/vapid-public-key")
    public VapidPublicKeyResponse vapidPublicKey() {
        return new VapidPublicKeyResponse(vapidPublicKey);
    }

    @PostMapping("/subscriptions")
    public void subscribe(
            @RequestBody SubscribeRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User user = requireUser(authorization);
        if (request.endpoint() == null || request.endpoint().isBlank() || request.keys() == null) {
            throw new IllegalArgumentException("구독 정보가 올바르지 않습니다.");
        }

        PushSubscription subscription = subscriptionRepository.findByEndpoint(request.endpoint())
                .orElseGet(() -> new PushSubscription(
                        user.getId(), request.endpoint(), request.keys().p256dh(), request.keys().auth()
                ));
        subscription.updateKeys(user.getId(), request.keys().p256dh(), request.keys().auth());
        subscriptionRepository.save(subscription);
    }

    @DeleteMapping("/subscriptions")
    public void unsubscribe(
            @RequestBody UnsubscribeRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        requireUser(authorization);
        if (request.endpoint() != null && !request.endpoint().isBlank()) {
            subscriptionRepository.deleteByEndpoint(request.endpoint());
        }
    }

    private User requireUser(String authorization) {
        String token = extractToken(authorization);
        return authService.validate(token).orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleInvalidCredentials(InvalidCredentialsException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}

package com.projectguard.backend.push;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.InvalidCredentialsException;
import com.projectguard.backend.consultation.SenderType;
import com.projectguard.backend.lawyer.LawyerAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모바일 앱이 FCM 기기 토큰을 등록/해지하는 API. 회원 토큰과 변호사 토큰 어느 쪽으로 로그인했든 받는다.
 * 앱은 로그인 직후 등록하고, 로그아웃 직전에 해지한다.
 */
@RestController
@RequestMapping("/api/push/devices")
public class DeviceTokenController {

    private final DeviceTokenRepository deviceTokenRepository;
    private final AuthService authService;
    private final LawyerAuthService lawyerAuthService;

    public DeviceTokenController(
            DeviceTokenRepository deviceTokenRepository,
            AuthService authService,
            LawyerAuthService lawyerAuthService
    ) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.authService = authService;
        this.lawyerAuthService = lawyerAuthService;
    }

    public record RegisterRequest(String token, String platform) {
    }

    public record UnregisterRequest(String token) {
    }

    private record Owner(SenderType type, Long id) {
    }

    @PostMapping
    public void register(
            @RequestBody RegisterRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Owner owner = requireOwner(authorization);
        if (request.token() == null || request.token().isBlank() || request.token().length() > 512) {
            throw new IllegalArgumentException("기기 토큰이 올바르지 않습니다.");
        }
        String platform = request.platform() != null && request.platform().length() <= 20 ? request.platform() : null;
        DeviceToken deviceToken = deviceTokenRepository.findByToken(request.token())
                .orElseGet(() -> new DeviceToken(owner.type(), owner.id(), request.token(), platform));
        deviceToken.assignTo(owner.type(), owner.id(), platform);
        deviceTokenRepository.save(deviceToken);
    }

    @DeleteMapping
    public void unregister(
            @RequestBody UnregisterRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Owner owner = requireOwner(authorization);
        if (request.token() == null || request.token().isBlank()) {
            return;
        }
        // 다른 계정의 토큰을 지우지 못하게 소유자가 같을 때만 지운다.
        deviceTokenRepository.findByToken(request.token())
                .filter(t -> t.getOwnerType() == owner.type() && t.getOwnerId().equals(owner.id()))
                .ifPresent(deviceTokenRepository::delete);
    }

    private Owner requireOwner(String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : null;
        if (token == null) {
            throw new InvalidCredentialsException("로그인이 필요합니다.");
        }
        return authService.validate(token)
                .map(u -> new Owner(SenderType.USER, u.getId()))
                .or(() -> lawyerAuthService.validate(token).map(l -> new Owner(SenderType.LAWYER, l.getId())))
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
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

package com.projectguard.backend.lawyer;

import com.projectguard.backend.auth.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한(승인된) 변호사 본인의 프로필/알림 설정. barNumber(등록번호)와 email은 신원 확인이
 * 끝난 값이라 자율 변경 대상에서 제외했다 — 바꾸려면 관리자 재검토가 필요하다고 판단.
 */
@RestController
@RequestMapping("/api/lawyer/profile")
public class LawyerProfileController {

    private final LawyerAuthService lawyerAuthService;

    public LawyerProfileController(LawyerAuthService lawyerAuthService) {
        this.lawyerAuthService = lawyerAuthService;
    }

    public record ProfileResponse(
            String email, String name, String lawFirm, String barNumber,
            String specialties, String introduction, boolean emailNotificationsEnabled, String status
    ) {
    }

    public record UpdateProfileRequest(String name, String lawFirm, String specialties, String introduction) {
    }

    public record NotificationSettingRequest(boolean enabled) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    @GetMapping
    public ProfileResponse get(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return toResponse(requireLawyer(authorization));
    }

    @PutMapping
    public ProfileResponse update(
            @RequestBody UpdateProfileRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Lawyer lawyer = requireLawyer(authorization);
        Lawyer updated = lawyerAuthService.updateProfile(
                lawyer.getId(), request.name(), request.lawFirm(), request.specialties(), request.introduction());
        return toResponse(updated);
    }

    @PutMapping("/notifications")
    public void updateNotifications(
            @RequestBody NotificationSettingRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Lawyer lawyer = requireLawyer(authorization);
        lawyerAuthService.updateEmailNotificationsEnabled(lawyer.getId(), request.enabled());
    }

    @PostMapping("/password")
    public void changePassword(
            @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Lawyer lawyer = requireLawyer(authorization);
        lawyerAuthService.changePassword(lawyer.getId(), request.currentPassword(), request.newPassword());
    }

    private Lawyer requireLawyer(String authorization) {
        String token = extractToken(authorization);
        return lawyerAuthService.validate(token).orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }

    private ProfileResponse toResponse(Lawyer lawyer) {
        return new ProfileResponse(
                lawyer.getEmail(), lawyer.getName(), lawyer.getLawFirm(), lawyer.getBarNumber(),
                lawyer.getSpecialties(), lawyer.getIntroduction(), lawyer.isEmailNotificationsEnabled(),
                lawyer.getStatus().name()
        );
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

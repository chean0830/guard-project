package com.projectguard.backend.auth;

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

/** 로그인한 회원 본인의 프로필(표시 이름)과 비밀번호 설정. */
@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private final AuthService authService;

    public UserProfileController(AuthService authService) {
        this.authService = authService;
    }

    public record ProfileResponse(String email, String name, String provider) {
    }

    public record UpdateProfileRequest(String name) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    @GetMapping
    public ProfileResponse get(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return toResponse(requireUser(authorization));
    }

    @PutMapping
    public ProfileResponse update(
            @RequestBody UpdateProfileRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User user = requireUser(authorization);
        authService.updateProfile(user.getId(), request.name());
        return toResponse(requireUser(authorization));
    }

    @PostMapping("/password")
    public void changePassword(
            @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User user = requireUser(authorization);
        authService.changePassword(user.getId(), request.currentPassword(), request.newPassword());
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

    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(user.getEmail(), user.getName(), user.getProvider());
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

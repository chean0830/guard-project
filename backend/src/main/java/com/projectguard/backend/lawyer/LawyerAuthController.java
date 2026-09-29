package com.projectguard.backend.lawyer;

import com.projectguard.backend.auth.EmailAlreadyExistsException;
import com.projectguard.backend.auth.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 변호사 전용 회원가입/로그인/로그아웃/세션 확인. 일반 회원(auth.AuthController)과 완전히
 * 별도 경로·별도 토큰 체계로 분리해, 변호사 계정과 일반 회원 계정이 섞이지 않게 한다.
 * 회원가입은 서류 제출까지만 하고, 실제 로그인은 관리자 승인 후에만 가능하다.
 */
@RestController
@RequestMapping("/api/lawyer/auth")
public class LawyerAuthController {

    private final LawyerAuthService lawyerAuthService;

    public LawyerAuthController(LawyerAuthService lawyerAuthService) {
        this.lawyerAuthService = lawyerAuthService;
    }

    public record LawyerLoginRequest(String email, String password) {
    }

    public record LawyerSignupResponse(String email, String status, String message) {
    }

    public record LawyerAuthResponse(String token, String email, String name, String status) {
    }

    @PostMapping(value = "/signup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LawyerSignupResponse signup(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String name,
            @RequestParam(required = false) String lawFirm,
            @RequestParam String barNumber,
            @RequestParam(value = "documents", required = false) List<MultipartFile> documents
    ) {
        Lawyer lawyer = lawyerAuthService.signup(email, password, name, lawFirm, barNumber, documents);
        return new LawyerSignupResponse(
                lawyer.getEmail(),
                lawyer.getStatus().name(),
                "제출이 완료되었습니다. 관리자가 서류를 검토한 뒤 승인되면 로그인하실 수 있습니다."
        );
    }

    @PostMapping("/login")
    public LawyerAuthResponse login(@RequestBody LawyerLoginRequest request) {
        LawyerAuthResult result = lawyerAuthService.login(request.email(), request.password());
        return new LawyerAuthResponse(result.token(), result.email(), result.name(), "APPROVED");
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        lawyerAuthService.logout(extractToken(authorization));
    }

    @GetMapping("/me")
    public LawyerAuthResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Lawyer lawyer = lawyerAuthService.validate(extractToken(authorization))
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
        return new LawyerAuthResponse(null, lawyer.getEmail(), lawyer.getName(), lawyer.getStatus().name());
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleEmailExists(EmailAlreadyExistsException e) {
        return e.getMessage();
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleInvalidCredentials(InvalidCredentialsException e) {
        return e.getMessage();
    }

    @ExceptionHandler(LawyerNotApprovedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleNotApproved(LawyerNotApprovedException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public String handleTooLarge(MaxUploadSizeExceededException e) {
        return "첨부 파일 용량이 너무 큽니다. 20MB 이하로 첨부해주세요.";
    }
}

package com.projectguard.backend.lawyer;

import com.projectguard.backend.auth.EmailAlreadyExistsException;
import com.projectguard.backend.auth.InvalidCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 변호사 회원가입은 일반 회원가입(auth.AuthService)과 달리 가입 즉시 로그인할 수 없다.
 * 자격 증명 서류를 제출하면 PENDING 상태로 저장되고, 관리자(LawyerAdminController)가 서류를
 * 검토해 승인해야 로그인이 열린다 — 아무나 "변호사입니다"라고 주장하며 가입해 변호사 상담
 * 기능을 악용하는 것을 막기 위함.
 */
@Service
public class LawyerAuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    private final LawyerRepository lawyerRepository;
    private final LawyerCredentialDocumentRepository documentRepository;
    private final LawyerAuthTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LawyerAuthService(
            LawyerRepository lawyerRepository,
            LawyerCredentialDocumentRepository documentRepository,
            LawyerAuthTokenRepository tokenRepository
    ) {
        this.lawyerRepository = lawyerRepository;
        this.documentRepository = documentRepository;
        this.tokenRepository = tokenRepository;
    }

    public Lawyer signup(
            String email,
            String password,
            String name,
            String lawFirm,
            String barNumber,
            List<MultipartFile> documents
    ) {
        validateSignupFields(email, password, name, barNumber);
        List<MultipartFile> attachments = documents == null
                ? List.of()
                : documents.stream().filter(file -> !file.isEmpty()).toList();
        if (attachments.isEmpty()) {
            throw new IllegalArgumentException("변호사 자격을 확인할 수 있는 서류를 1개 이상 첨부해주세요.");
        }
        if (lawyerRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyExistsException("이미 가입 신청된 이메일입니다.");
        }

        Lawyer lawyer = lawyerRepository.save(
                new Lawyer(email, passwordEncoder.encode(password), name, lawFirm, barNumber)
        );
        for (MultipartFile file : attachments) {
            documentRepository.save(new LawyerCredentialDocument(
                    lawyer, file.getOriginalFilename(), file.getContentType(), readBytes(file)
            ));
        }
        return lawyer;
    }

    public LawyerAuthResult login(String email, String password) {
        Lawyer lawyer = lawyerRepository.findByEmail(email)
                .filter(l -> passwordEncoder.matches(password, l.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));

        switch (lawyer.getStatus()) {
            case PENDING -> throw new LawyerNotApprovedException(
                    "관리자 승인 대기 중입니다. 제출하신 서류 검토 후 로그인하실 수 있습니다.");
            case REJECTED -> throw new LawyerNotApprovedException(
                    "가입 승인이 거절되었습니다." + (lawyer.getRejectionReason() != null && !lawyer.getRejectionReason().isBlank()
                            ? " 사유: " + lawyer.getRejectionReason()
                            : ""));
            case APPROVED -> { }
        }
        return issueToken(lawyer);
    }

    public Optional<Lawyer> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository.findById(token)
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .flatMap(t -> lawyerRepository.findById(t.getLawyerId()));
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            tokenRepository.deleteById(token);
        }
    }

    public Lawyer updateProfile(Long lawyerId, String name, String lawFirm, String specialties, String introduction) {
        Lawyer lawyer = requireLawyer(lawyerId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름을 입력해주세요.");
        }
        lawyer.updateProfile(name, lawFirm, specialties, introduction);
        return lawyerRepository.save(lawyer);
    }

    public void updateEmailNotificationsEnabled(Long lawyerId, boolean enabled) {
        Lawyer lawyer = requireLawyer(lawyerId);
        lawyer.setEmailNotificationsEnabled(enabled);
        lawyerRepository.save(lawyer);
    }

    public void changePassword(Long lawyerId, String currentPassword, String newPassword) {
        Lawyer lawyer = requireLawyer(lawyerId);
        if (!passwordEncoder.matches(currentPassword, lawyer.getPasswordHash())) {
            throw new InvalidCredentialsException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("새 비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
        lawyer.changePasswordHash(passwordEncoder.encode(newPassword));
        lawyerRepository.save(lawyer);
    }

    private Lawyer requireLawyer(Long lawyerId) {
        return lawyerRepository.findById(lawyerId)
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
    }

    private LawyerAuthResult issueToken(Lawyer lawyer) {
        String token = UUID.randomUUID().toString();
        tokenRepository.save(new LawyerAuthToken(token, lawyer.getId(), Instant.now().plus(TOKEN_TTL)));
        return new LawyerAuthResult(token, lawyer.getEmail(), lawyer.getName());
    }

    private void validateSignupFields(String email, String password, String name, String barNumber) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("올바른 이메일 주소를 입력해주세요.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름을 입력해주세요.");
        }
        if (barNumber == null || barNumber.isBlank()) {
            throw new IllegalArgumentException("변호사 등록번호를 입력해주세요.");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("첨부 파일을 읽는 중 오류가 발생했습니다.", e);
        }
    }
}

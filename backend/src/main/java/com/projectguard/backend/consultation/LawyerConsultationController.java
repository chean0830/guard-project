package com.projectguard.backend.consultation;

import com.projectguard.backend.auth.InvalidCredentialsException;
import com.projectguard.backend.auth.UserRepository;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 승인된 변호사가 로그인 후 자신에게 온 문의를 확인/답장하는 API. 변호사 세션
 * (lawyer.LawyerAuthService) 토큰으로 인증하며, 자신에게 매칭된 문의가 아니면 볼 수 없다.
 */
@RestController
@RequestMapping("/api/lawyer/consultations")
public class LawyerConsultationController {

    private final ConsultationService consultationService;
    private final LawyerAuthService lawyerAuthService;
    private final UserRepository userRepository;

    public LawyerConsultationController(
            ConsultationService consultationService,
            LawyerAuthService lawyerAuthService,
            UserRepository userRepository
    ) {
        this.consultationService = consultationService;
        this.lawyerAuthService = lawyerAuthService;
        this.userRepository = userRepository;
    }

    public record MessageRequest(String content) {
    }

    public record MessageDto(String senderType, String content, Instant createdAt) {
    }

    public record ConsultationSummaryDto(
            Long id, String userDisplayName, String lastMessagePreview, Instant lastMessageAt, long unreadCount
    ) {
    }

    public record ConsultationThreadDto(String userDisplayName, List<MessageDto> messages) {
    }

    @GetMapping
    public List<ConsultationSummaryDto> list(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Lawyer lawyer = requireLawyer(authorization);
        return consultationService.listForLawyer(lawyer.getId()).stream().map(this::toSummary).toList();
    }

    @GetMapping("/{id}")
    public ConsultationThreadDto thread(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Lawyer lawyer = requireLawyer(authorization);
        List<ConsultationMessage> messages = consultationService.getThreadAsLawyer(id, lawyer.getId());
        Consultation consultation = consultationService.requireConsultation(id);
        return new ConsultationThreadDto(displayNameFor(consultation.getUserId()), messages.stream().map(this::toDto).toList());
    }

    @PostMapping("/{id}/messages")
    public MessageDto postMessage(
            @PathVariable Long id,
            @RequestBody MessageRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Lawyer lawyer = requireLawyer(authorization);
        ConsultationMessage message = consultationService.postMessage(id, SenderType.LAWYER, lawyer.getId(), request.content());
        return toDto(message);
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

    private String displayNameFor(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getName() != null && !u.getName().isBlank() ? u.getName() : u.getEmail())
                .orElse("알 수 없음");
    }

    private MessageDto toDto(ConsultationMessage message) {
        return new MessageDto(message.getSenderType().name(), message.getContent(), message.getCreatedAt());
    }

    private ConsultationSummaryDto toSummary(Consultation consultation) {
        String preview = consultationService.getLastMessagePreview(consultation.getId());
        long unread = consultationService.unreadCountForLawyer(consultation.getId());
        return new ConsultationSummaryDto(
                consultation.getId(),
                displayNameFor(consultation.getUserId()),
                preview,
                consultation.getLastMessageAt(),
                unread
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleInvalidCredentials(InvalidCredentialsException e) {
        return e.getMessage();
    }

    @ExceptionHandler(ConsultationAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(ConsultationAccessDeniedException e) {
        return e.getMessage();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoSuchElementException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}

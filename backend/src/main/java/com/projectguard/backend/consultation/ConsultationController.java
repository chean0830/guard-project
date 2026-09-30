package com.projectguard.backend.consultation;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.InvalidCredentialsException;
import com.projectguard.backend.auth.User;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerRepository;
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
 * 로그인한 회원이 변호사에게 문의를 시작하고 대화를 이어가는 API. 회원 세션(auth.AuthService)
 * 토큰으로 인증하며, 자신이 시작한 문의가 아니면 볼 수 없다(ConsultationService에서 검증).
 */
@RestController
@RequestMapping("/api/consultations")
public class ConsultationController {

    private final ConsultationService consultationService;
    private final AuthService authService;
    private final LawyerRepository lawyerRepository;

    public ConsultationController(
            ConsultationService consultationService,
            AuthService authService,
            LawyerRepository lawyerRepository
    ) {
        this.consultationService = consultationService;
        this.authService = authService;
        this.lawyerRepository = lawyerRepository;
    }

    public record StartRequest(String message) {
    }

    public record MessageRequest(String content) {
    }

    public record MessageDto(String senderType, String content, Instant createdAt) {
    }

    public record ConsultationSummaryDto(
            Long id, Long lawyerId, String lawyerName, String lawFirm, String lastMessagePreview, Instant lastMessageAt, long unreadCount
    ) {
    }

    public record ConsultationThreadDto(
            String lawyerName, String lawFirm, boolean counterpartBlocked, String blockState, List<MessageDto> messages
    ) {
    }

    @PostMapping
    public ConsultationSummaryDto start(
            @RequestBody StartRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User user = requireUser(authorization);
        Consultation consultation = consultationService.startConsultation(user.getId(), request.message());
        return toSummary(consultation);
    }

    @GetMapping
    public List<ConsultationSummaryDto> list(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User user = requireUser(authorization);
        return consultationService.listForUser(user.getId()).stream().map(this::toSummary).toList();
    }

    @GetMapping("/{id}")
    public ConsultationThreadDto thread(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User user = requireUser(authorization);
        List<ConsultationMessage> messages = consultationService.getThreadAsUser(id, user.getId());
        Consultation consultation = consultationService.requireConsultation(id);
        Lawyer lawyer = lawyerRepository.findById(consultation.getLawyerId()).orElse(null);
        return new ConsultationThreadDto(
                lawyer != null ? lawyer.getName() : "알 수 없음",
                lawyer != null ? lawyer.getLawFirm() : null,
                consultationService.isCounterpartBlocked(consultation, SenderType.USER),
                consultationService.blockStateFor(consultation, SenderType.USER),
                messages.stream().map(this::toDto).toList()
        );
    }

    @PostMapping("/{id}/messages")
    public MessageDto postMessage(
            @PathVariable Long id,
            @RequestBody MessageRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        User user = requireUser(authorization);
        ConsultationMessage message = consultationService.postMessage(id, SenderType.USER, user.getId(), request.content());
        return toDto(message);
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

    private MessageDto toDto(ConsultationMessage message) {
        return new MessageDto(message.getSenderType().name(), message.getContent(), message.getCreatedAt());
    }

    private ConsultationSummaryDto toSummary(Consultation consultation) {
        Lawyer lawyer = lawyerRepository.findById(consultation.getLawyerId()).orElse(null);
        String preview = consultationService.getLastMessagePreview(consultation.getId());
        long unread = consultationService.unreadCountForUser(consultation.getId());
        return new ConsultationSummaryDto(
                consultation.getId(),
                consultation.getLawyerId(),
                lawyer != null ? lawyer.getName() : "알 수 없음",
                lawyer != null ? lawyer.getLawFirm() : null,
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

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(RuntimeException e) {
        return e.getMessage();
    }
}

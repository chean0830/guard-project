package com.projectguard.backend.lawyer;

import com.projectguard.backend.auth.InvalidCredentialsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 변호사 가입 신청을 검토/승인/거절하는 관리자 전용 API. 별도 관리자 계정 체계를 둘 만큼
 * 규모가 크지 않은 포트폴리오 프로젝트라, 서버 환경변수(ADMIN_SECRET)를 아는 사람만 호출할 수
 * 있게 하는 방식을 택함 (oauth-sync의 INTERNAL_SYNC_SECRET과 같은 패턴).
 */
@RestController
@RequestMapping("/api/admin/lawyers")
public class LawyerAdminController {

    private final LawyerRepository lawyerRepository;
    private final LawyerCredentialDocumentRepository documentRepository;
    private final String adminSecret;

    public LawyerAdminController(
            LawyerRepository lawyerRepository,
            LawyerCredentialDocumentRepository documentRepository,
            @Value("${ADMIN_SECRET:}") String adminSecret
    ) {
        this.lawyerRepository = lawyerRepository;
        this.documentRepository = documentRepository;
        this.adminSecret = adminSecret;
    }

    public record DocumentSummary(Long id, String fileName, String contentType) {
    }

    public record LawyerSummary(
            Long id,
            String email,
            String name,
            String lawFirm,
            String barNumber,
            String status,
            String rejectionReason,
            Instant createdAt,
            List<DocumentSummary> documents
    ) {
    }

    public record RejectRequest(String reason) {
    }

    @GetMapping
    public List<LawyerSummary> list(
            @RequestHeader(value = "X-Admin-Secret", required = false) String secret,
            @RequestParam(required = false) LawyerStatus status
    ) {
        requireAdmin(secret);
        List<Lawyer> lawyers = status != null ? lawyerRepository.findByStatus(status) : lawyerRepository.findAll();
        return lawyers.stream().map(this::toSummary).toList();
    }

    @GetMapping("/{id}/documents/{documentId}")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long id,
            @PathVariable Long documentId,
            @RequestHeader(value = "X-Admin-Secret", required = false) String secret
    ) {
        requireAdmin(secret);
        LawyerCredentialDocument document = documentRepository.findById(documentId)
                .filter(d -> d.getLawyer().getId().equals(id))
                .orElseThrow(() -> new NoSuchElementException("서류를 찾을 수 없습니다."));

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(document.getContentType());
        } catch (RuntimeException e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + document.getFileName() + "\"")
                .body(document.getData());
    }

    @PostMapping("/{id}/approve")
    public void approve(
            @PathVariable Long id,
            @RequestHeader(value = "X-Admin-Secret", required = false) String secret
    ) {
        requireAdmin(secret);
        Lawyer lawyer = findLawyer(id);
        lawyer.approve();
        lawyerRepository.save(lawyer);
    }

    @PostMapping("/{id}/reject")
    public void reject(
            @PathVariable Long id,
            @RequestBody(required = false) RejectRequest request,
            @RequestHeader(value = "X-Admin-Secret", required = false) String secret
    ) {
        requireAdmin(secret);
        Lawyer lawyer = findLawyer(id);
        lawyer.reject(request != null ? request.reason() : null);
        lawyerRepository.save(lawyer);
    }

    private Lawyer findLawyer(Long id) {
        return lawyerRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("가입 신청을 찾을 수 없습니다."));
    }

    private LawyerSummary toSummary(Lawyer lawyer) {
        List<DocumentSummary> documents = documentRepository.findByLawyerId(lawyer.getId()).stream()
                .map(d -> new DocumentSummary(d.getId(), d.getFileName(), d.getContentType()))
                .toList();
        return new LawyerSummary(
                lawyer.getId(),
                lawyer.getEmail(),
                lawyer.getName(),
                lawyer.getLawFirm(),
                lawyer.getBarNumber(),
                lawyer.getStatus().name(),
                lawyer.getRejectionReason(),
                lawyer.getCreatedAt(),
                documents
        );
    }

    private void requireAdmin(String providedSecret) {
        if (adminSecret.isBlank() || !adminSecret.equals(providedSecret)) {
            throw new InvalidCredentialsException("관리자 전용 엔드포인트입니다.");
        }
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleInvalidCredentials(InvalidCredentialsException e) {
        return e.getMessage();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoSuchElementException e) {
        return e.getMessage();
    }
}

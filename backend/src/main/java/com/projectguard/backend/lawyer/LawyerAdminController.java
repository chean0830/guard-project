package com.projectguard.backend.lawyer;

import com.projectguard.backend.common.AccountMailService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 변호사 가입 신청을 검토/승인/거절하는 관리자 전용 API. 관리자 인증은 /api/admin/** 전체에
 * 걸린 admin.AdminAuthInterceptor가 처리한다(관리자 세션 토큰 또는 ADMIN_SECRET).
 */
@RestController
@RequestMapping("/api/admin/lawyers")
public class LawyerAdminController {

    private final LawyerRepository lawyerRepository;
    private final LawyerCredentialDocumentRepository documentRepository;
    private final AccountMailService mailService;
    private final LawyerAuthTokenRepository tokenRepository;

    public LawyerAdminController(
            LawyerRepository lawyerRepository,
            LawyerCredentialDocumentRepository documentRepository,
            AccountMailService mailService,
            LawyerAuthTokenRepository tokenRepository
    ) {
        this.lawyerRepository = lawyerRepository;
        this.documentRepository = documentRepository;
        this.mailService = mailService;
        this.tokenRepository = tokenRepository;
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
            @RequestParam(required = false) LawyerStatus status
    ) {
        List<Lawyer> lawyers = status != null ? lawyerRepository.findByStatus(status) : lawyerRepository.findAll();
        return lawyers.stream().map(this::toSummary).toList();
    }

    @GetMapping("/{id}/documents/{documentId}")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long id,
            @PathVariable Long documentId
    ) {
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
            @PathVariable Long id
    ) {
        Lawyer lawyer = findLawyer(id);
        lawyer.approve();
        lawyerRepository.save(lawyer);
        mailService.send(lawyer.getEmail(), "변호사 가입이 승인되었습니다",
                lawyer.getName() + " 변호사님, 제출하신 자격 서류 확인이 끝나 가입이 승인되었습니다.\n\n"
                        + "이제 변호사 로그인 후 회원 문의에 답변하실 수 있습니다.\n"
                        + "설정 화면에서 강점·수임료·실적을 입력하시면 회원이 변호사를 고를 때 참고합니다.");
    }

    @PostMapping("/{id}/reject")
    public void reject(
            @PathVariable Long id,
            @RequestBody(required = false) RejectRequest request
    ) {
        Lawyer lawyer = findLawyer(id);
        lawyer.reject(request != null ? request.reason() : null);
        lawyerRepository.save(lawyer);
        String reason = lawyer.getRejectionReason();
        mailService.send(lawyer.getEmail(), "변호사 가입 신청 결과 안내",
                lawyer.getName() + " 변호사님, 아쉽게도 이번 가입 신청은 승인되지 않았습니다.\n\n"
                        + (reason != null && !reason.isBlank() ? "사유: " + reason + "\n\n" : "")
                        + "서류를 보완해 다시 신청하시거나, 문의가 있으면 회신해주세요.");
    }

    /**
     * 승인 취소: 승인 대기로 되돌리고 로그인 세션을 모두 끊는다. 이후 새 문의 배정·변호사 목록에서 빠지고 다시 로그인할 수 없다.
     * 이미 진행 중인 상담 대화는 남는다 (회원 쪽 기록 보존).
     */
    @PostMapping("/{id}/revoke")
    public void revoke(
            @PathVariable Long id
    ) {
        Lawyer lawyer = findLawyer(id);
        lawyer.revokeApproval();
        lawyerRepository.save(lawyer);
        tokenRepository.deleteByLawyerId(id);
        mailService.send(lawyer.getEmail(), "변호사 승인이 취소되었습니다",
                lawyer.getName() + " 변호사님, 관리자 검토에 따라 가입 승인이 취소되어 승인 대기 상태로 바뀌었습니다.\n\n"
                        + "다시 승인되기 전까지는 로그인과 새 상담 배정이 제한됩니다. 문의가 있으면 회신해주세요.");
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



    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleConflict(IllegalStateException e) {
        return e.getMessage();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoSuchElementException e) {
        return e.getMessage();
    }
}

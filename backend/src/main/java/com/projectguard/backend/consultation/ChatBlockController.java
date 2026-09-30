package com.projectguard.backend.consultation;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.InvalidCredentialsException;
import com.projectguard.backend.auth.User;
import com.projectguard.backend.auth.UserRepository;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerAuthService;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 차단 API. 회원은 회원 세션으로(/api/consultations/..., /api/blocks), 변호사는 변호사 세션으로
 * (/api/lawyer/consultations/..., /api/lawyer/blocks) 각자 호출한다.
 */
@RestController
public class ChatBlockController {

    private final ChatBlockService blockService;
    private final AuthService authService;
    private final LawyerAuthService lawyerAuthService;
    private final UserRepository userRepository;
    private final LawyerRepository lawyerRepository;

    public ChatBlockController(
            ChatBlockService blockService,
            AuthService authService,
            LawyerAuthService lawyerAuthService,
            UserRepository userRepository,
            LawyerRepository lawyerRepository
    ) {
        this.blockService = blockService;
        this.authService = authService;
        this.lawyerAuthService = lawyerAuthService;
        this.userRepository = userRepository;
        this.lawyerRepository = lawyerRepository;
    }

    /** 차단 목록 한 줄. name은 상대방 표시 이름, detail은 보조 정보(변호사 소속 등). */
    public record BlockedEntry(Long id, String name, String detail, Instant blockedAt) {
    }

    @PostMapping("/api/consultations/{id}/block")
    public void blockAsUser(@PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String authorization) {
        blockService.blockCounterpart(id, SenderType.USER, requireUser(authorization).getId());
    }

    @PostMapping("/api/lawyer/consultations/{id}/block")
    public void blockAsLawyer(@PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String authorization) {
        blockService.blockCounterpart(id, SenderType.LAWYER, requireLawyer(authorization).getId());
    }

    @GetMapping("/api/blocks")
    public List<BlockedEntry> listForUser(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return blockService.listMine(SenderType.USER, requireUser(authorization).getId()).stream()
                .map(b -> {
                    Lawyer lawyer = lawyerRepository.findById(b.getLawyerId()).orElse(null);
                    return new BlockedEntry(b.getId(),
                            lawyer != null ? lawyer.getName() + " 변호사" : "알 수 없음",
                            lawyer != null ? lawyer.getLawFirm() : null,
                            b.getCreatedAt());
                })
                .toList();
    }

    @GetMapping("/api/lawyer/blocks")
    public List<BlockedEntry> listForLawyer(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return blockService.listMine(SenderType.LAWYER, requireLawyer(authorization).getId()).stream()
                .map(b -> {
                    User user = userRepository.findById(b.getUserId()).orElse(null);
                    String name = user == null ? "알 수 없음"
                            : user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail();
                    return new BlockedEntry(b.getId(), name, "회원", b.getCreatedAt());
                })
                .toList();
    }

    @DeleteMapping("/api/blocks/{blockId}")
    public void unblockAsUser(@PathVariable Long blockId, @RequestHeader(value = "Authorization", required = false) String authorization) {
        blockService.unblock(blockId, SenderType.USER, requireUser(authorization).getId());
    }

    @DeleteMapping("/api/lawyer/blocks/{blockId}")
    public void unblockAsLawyer(@PathVariable Long blockId, @RequestHeader(value = "Authorization", required = false) String authorization) {
        blockService.unblock(blockId, SenderType.LAWYER, requireLawyer(authorization).getId());
    }

    private User requireUser(String authorization) {
        return authService.validate(extractToken(authorization))
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
    }

    private Lawyer requireLawyer(String authorization) {
        return lawyerAuthService.validate(extractToken(authorization))
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
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
}

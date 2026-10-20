package com.projectguard.backend.api;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.InvalidCredentialsException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 등기부 파일을 브라우저에서 이 서버로 바로 올리기 위한 1회용 입장권.
 * 웹은 원래 Next.js 서버를 거쳐 파일을 넘겼는데, Server Action은 요청 본문이 1MB를 넘으면 거절하고
 * (2MB 파일로 직접 재현) 배포 환경(Vercel)은 함수 요청 자체를 4.5MB로 막아 촬영 사진 여러 장을 넘길 수 없다.
 * 로그인 토큰은 httpOnly 쿠키라 브라우저가 꺼낼 수 없으므로, Next.js 서버가 쿠키로 인증해 이 입장권을 받아
 * 넘기고 브라우저는 입장권으로만 업로드한다 (상담 채팅의 ConsultationSocketTicketService와 같은 방식).
 * 60초 안에 한 번만 쓸 수 있다. (서버 한 대 기준 메모리 보관)
 */
@Service
public class AnalysisUploadTicketService {

    private static final Duration TICKET_TTL = Duration.ofSeconds(60);

    private record Ticket(String sessionToken, Instant expiresAt) {
    }

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final AuthService authService;

    public AnalysisUploadTicketService(AuthService authService) {
        this.authService = authService;
    }

    /** 로그인한 회원에게만 발급한다. */
    public String issue(String sessionToken) {
        authService.validate(sessionToken)
                .orElseThrow(() -> new InvalidCredentialsException("로그인이 필요합니다."));
        Instant now = Instant.now();
        tickets.values().removeIf(t -> t.expiresAt().isBefore(now));
        String value = UUID.randomUUID().toString();
        tickets.put(value, new Ticket(sessionToken, now.plus(TICKET_TTL)));
        return value;
    }

    /** 입장권을 꺼내면서 폐기하고(1회용) 발급받은 회원의 세션 토큰을 돌려준다. 없거나 만료됐으면 비어 있다. */
    public Optional<String> consume(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tickets.remove(value))
                .filter(t -> t.expiresAt().isAfter(Instant.now()))
                .map(Ticket::sessionToken);
    }
}

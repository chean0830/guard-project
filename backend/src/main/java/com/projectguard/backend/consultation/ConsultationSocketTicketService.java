package com.projectguard.backend.consultation;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 연결용 1회용 입장권. 로그인 토큰은 httpOnly 쿠키라 브라우저 스크립트가 꺼낼 수 없고,
 * 꺼낼 수 있게 만들어서도 안 된다 — 그래서 Next.js 서버가 쿠키로 인증해 이 입장권을 받아 브라우저에
 * 넘기고, 브라우저는 입장권으로만 WebSocket에 접속한다. 입장권은 특정 대화방·참여자에 묶여 있고
 * 60초 안에 한 번만 쓸 수 있어서, 새어 나가도 재사용할 수 없다.
 * (서버 한 대 기준 메모리 보관. 여러 대로 늘리면 공유 저장소로 옮겨야 한다.)
 */
@Service
public class ConsultationSocketTicketService {

    private static final Duration TICKET_TTL = Duration.ofSeconds(60);

    /** consultationId가 null이면 대화방이 아니라 상담 목록(InboxSocketHandler) 입장권이다. */
    public record Ticket(Long consultationId, SenderType participantType, Long participantId, Instant expiresAt) {

        boolean isInbox() {
            return consultationId == null;
        }
    }

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final ConsultationService consultationService;

    public ConsultationSocketTicketService(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    /** 대화방 당사자인지 검증한 뒤 입장권을 발급한다. */
    public String issue(Long consultationId, SenderType participantType, Long participantId) {
        consultationService.requireConsultationFor(consultationId, participantType, participantId);
        return store(consultationId, participantType, participantId);
    }

    /** 상담 목록 알림용 입장권. 로그인한 본인(회원 또는 변호사)의 목록에만 묶인다. */
    public String issueForInbox(SenderType ownerType, Long ownerId) {
        return store(null, ownerType, ownerId);
    }

    private String store(Long consultationId, SenderType participantType, Long participantId) {
        Instant now = Instant.now();
        tickets.values().removeIf(t -> t.expiresAt().isBefore(now));
        String value = UUID.randomUUID().toString();
        tickets.put(value, new Ticket(consultationId, participantType, participantId, now.plus(TICKET_TTL)));
        return value;
    }

    /** 입장권을 꺼내면서 폐기한다(1회용). 없거나 만료됐으면 비어 있다. */
    public Optional<Ticket> consume(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tickets.remove(value))
                .filter(t -> t.expiresAt().isAfter(Instant.now()));
    }
}

package com.projectguard.backend.consultation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 변호사 상담 목록 실시간 알림. 대화방 소켓(ConsultationSocketHandler)이 대화방 하나에 묶인다면, 이 소켓은
 * 변호사 한 명에 묶여 그 변호사에게 배정된 모든 대화방의 변화(새 문의·새 메시지)를 알린다.
 * 알림에는 어떤 대화방이 바뀌었는지만 담고, 받는 화면이 목록을 다시 불러와 미리보기·안 읽은 수를 갱신한다.
 */
@Component
public class LawyerInboxSocketHandler extends TextWebSocketHandler {

    static final String LAWYER_ID_ATTRIBUTE = "lawyerId";

    private static final Logger log = LoggerFactory.getLogger(LawyerInboxSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 64 * 1024;

    private final Map<Long, Set<WebSocketSession>> sessionsByLawyer = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public LawyerInboxSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long lawyerId = (Long) session.getAttributes().get(LAWYER_ID_ATTRIBUTE);
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
        sessionsByLawyer.computeIfAbsent(lawyerId, id -> ConcurrentHashMap.newKeySet()).add(safe);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long lawyerId = (Long) session.getAttributes().get(LAWYER_ID_ATTRIBUTE);
        Set<WebSocketSession> sessions = sessionsByLawyer.get(lawyerId);
        if (sessions != null) {
            sessions.removeIf(s -> s.getId().equals(session.getId()));
            if (sessions.isEmpty()) {
                sessionsByLawyer.remove(lawyerId, sessions);
            }
        }
    }

    @EventListener
    public void onInboxChanged(LawyerInboxChangedEvent event) {
        Set<WebSocketSession> sessions = sessionsByLawyer.get(event.lawyerId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage payload = new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "inbox",
                "reason", event.reason().name(),
                "consultationId", event.consultationId()
        )));
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(payload);
                }
            } catch (IOException | RuntimeException e) {
                // 한 화면으로의 전송 실패가 메시지 저장이나 다른 화면 전송을 막으면 안 된다.
                log.warn("상담 목록 알림 전송 실패 (변호사 {}): {}", event.lawyerId(), e.getMessage());
            }
        }
    }
}

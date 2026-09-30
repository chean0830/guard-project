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
 * 상담 대화방 실시간 알림. 브라우저는 메시지를 기존 HTTP API로 보내고, 이 핸들러는 새 메시지가
 * 저장될 때마다 같은 대화방에 연결된 모든 화면(회원·변호사, 여러 탭 포함)으로 즉시 내려보내기만 한다.
 * 받는 쪽 화면은 알림을 받으면 스레드를 다시 불러와 읽음 처리까지 기존 흐름을 그대로 탄다.
 */
@Component
public class ConsultationSocketHandler extends TextWebSocketHandler {

    static final String CONSULTATION_ID_ATTRIBUTE = "consultationId";

    private static final Logger log = LoggerFactory.getLogger(ConsultationSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 64 * 1024;

    private final Map<Long, Set<WebSocketSession>> sessionsByConsultation = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public ConsultationSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long consultationId = (Long) session.getAttributes().get(CONSULTATION_ID_ATTRIBUTE);
        // 여러 스레드(이벤트 발행 스레드들)가 같은 세션에 동시에 쓰지 않도록 감싼다.
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
        sessionsByConsultation.computeIfAbsent(consultationId, id -> ConcurrentHashMap.newKeySet()).add(safe);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long consultationId = (Long) session.getAttributes().get(CONSULTATION_ID_ATTRIBUTE);
        Set<WebSocketSession> sessions = sessionsByConsultation.get(consultationId);
        if (sessions != null) {
            sessions.removeIf(s -> s.getId().equals(session.getId()));
            if (sessions.isEmpty()) {
                sessionsByConsultation.remove(consultationId, sessions);
            }
        }
    }

    @EventListener
    public void onMessagePosted(ConsultationMessagePostedEvent event) {
        Set<WebSocketSession> sessions = sessionsByConsultation.get(event.consultationId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage payload = new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "message",
                "senderType", event.senderType().name(),
                "content", event.content(),
                "createdAt", event.createdAt().toString()
        )));
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(payload);
                }
            } catch (IOException | RuntimeException e) {
                // 한 화면으로의 전송 실패가 메시지 저장이나 다른 화면 전송을 막으면 안 된다(푸시·메일과 같은 원칙).
                log.warn("실시간 알림 전송 실패 (대화방 {}): {}", event.consultationId(), e.getMessage());
            }
        }
    }

    int connectedSessionCount(Long consultationId) {
        Set<WebSocketSession> sessions = sessionsByConsultation.get(consultationId);
        return sessions == null ? 0 : sessions.size();
    }
}

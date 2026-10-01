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
 * 상담 목록 실시간 알림 (변호사 /ws/lawyer-inbox, 회원 /ws/user-inbox). 대화방 소켓(ConsultationSocketHandler)이
 * 대화방 하나에 묶인다면, 이 소켓은 사람 한 명에 묶여 그 사람의 모든 대화방 변화(새 문의·새 메시지)를 알린다.
 * 알림에는 어떤 대화방이 바뀌었는지만 담고, 받는 화면이 목록을 다시 불러와 미리보기·안 읽은 수를 갱신한다.
 */
@Component
public class InboxSocketHandler extends TextWebSocketHandler {

    static final String OWNER_TYPE_ATTRIBUTE = "inboxOwnerType";
    static final String OWNER_ID_ATTRIBUTE = "inboxOwnerId";

    private static final Logger log = LoggerFactory.getLogger(InboxSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 64 * 1024;

    private final Map<String, Set<WebSocketSession>> sessionsByOwner = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public InboxSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private static String key(SenderType ownerType, Long ownerId) {
        return ownerType.name() + ":" + ownerId;
    }

    private static String keyOf(WebSocketSession session) {
        return key((SenderType) session.getAttributes().get(OWNER_TYPE_ATTRIBUTE),
                (Long) session.getAttributes().get(OWNER_ID_ATTRIBUTE));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
        sessionsByOwner.computeIfAbsent(keyOf(session), k -> ConcurrentHashMap.newKeySet()).add(safe);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String key = keyOf(session);
        Set<WebSocketSession> sessions = sessionsByOwner.get(key);
        if (sessions != null) {
            sessions.removeIf(s -> s.getId().equals(session.getId()));
            if (sessions.isEmpty()) {
                sessionsByOwner.remove(key, sessions);
            }
        }
    }

    /** 이 계정의 목록 소켓이 서버에 등록됐는지 (테스트에서 연결 직후 경쟁 상태를 피하는 용도). */
    boolean hasSession(SenderType ownerType, Long ownerId) {
        Set<WebSocketSession> sessions = sessionsByOwner.get(key(ownerType, ownerId));
        return sessions != null && !sessions.isEmpty();
    }

    @EventListener
    public void onInboxChanged(InboxChangedEvent event) {
        Set<WebSocketSession> sessions = sessionsByOwner.get(key(event.ownerType(), event.ownerId()));
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
                log.warn("상담 목록 알림 전송 실패 ({} {}): {}", event.ownerType(), event.ownerId(), e.getMessage());
            }
        }
    }
}

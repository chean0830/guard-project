package com.projectguard.backend.consultation;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerAuthService;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 서버 포트에 WebSocket으로 접속해서, 상대방이 HTTP로 보낸 메시지가 즉시 내려오는지와
 * 입장권 없이/재사용/다른 사이트 출처로는 접속할 수 없는지를 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsultationSocketTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ConsultationService consultationService;

    @Autowired
    private ConsultationSocketTicketService ticketService;

    @Autowired
    private AuthService authService;

    @Autowired
    private LawyerAuthService lawyerAuthService;

    @Autowired
    private LawyerRepository lawyerRepository;

    @Autowired
    private InboxSocketHandler inboxSocketHandler;

    private Long userId;
    private Long lawyerId;
    private Long consultationId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        lawyerRepository.findAll().forEach(l -> {
            l.reject("테스트 격리");
            lawyerRepository.save(l);
        });
        Lawyer lawyer = lawyerAuthService.signup("ws-lawyer-" + suffix + "@example.com", "password123", "김변호", null, "12345",
                List.of(new MockMultipartFile("documents", "license.pdf", "application/pdf", "%PDF-1.4 dummy".getBytes())));
        lawyer.approve();
        lawyerRepository.save(lawyer);
        lawyerId = lawyer.getId();

        String token = authService.signup("ws-user-" + suffix + "@example.com", "password123").token();
        userId = authService.validate(token).orElseThrow().getId();
        consultationId = consultationService.startConsultation(userId, "첫 문의").getId();
    }

    private void awaitInboxRegistered(SenderType ownerType, Long ownerId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (!inboxSocketHandler.hasSession(ownerType, ownerId)) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("목록 소켓이 3초 안에 서버에 등록되지 않았다");
            }
            Thread.sleep(20);
        }
    }

    private record Connection(WebSocketSession session, BlockingQueue<String> received) {
    }

    private Connection connect(String ticket, String origin) throws Exception {
        return connect("/ws/consultations", ticket, origin);
    }

    private Connection connect(String path, String ticket, String origin) throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        WebSocketSession session = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                        received.add(message.getPayload());
                    }
                }, headers, URI.create("ws://localhost:" + port + path + "?ticket=" + ticket))
                .get(5, TimeUnit.SECONDS);
        return new Connection(session, received);
    }

    @Test
    void 변호사가_답장하면_회원_화면으로_즉시_내려온다() throws Exception {
        Connection user = connect(ticketService.issue(consultationId, SenderType.USER, userId), "http://localhost:3000");

        consultationService.postMessage(consultationId, SenderType.LAWYER, lawyerId, "답변드립니다");

        String payload = user.received().poll(3, TimeUnit.SECONDS);
        assertNotNull(payload, "3초 안에 알림이 와야 한다");
        assertTrue(payload.contains("\"senderType\":\"LAWYER\""));
        assertTrue(payload.contains("답변드립니다"));
        user.session().close();
    }

    @Test
    void 회원이_보내면_변호사_화면으로_내려오고_다른_대화방에는_가지_않는다() throws Exception {
        Connection lawyer = connect(ticketService.issue(consultationId, SenderType.LAWYER, lawyerId), "http://localhost:3000");
        Long otherConsultation = consultationService.startConsultation(userId, "다른 문의").getId();

        consultationService.postMessage(otherConsultation, SenderType.USER, userId, "다른 방 메시지");
        consultationService.postMessage(consultationId, SenderType.USER, userId, "이 방 메시지");

        String payload = lawyer.received().poll(3, TimeUnit.SECONDS);
        assertNotNull(payload);
        assertTrue(payload.contains("이 방 메시지"), payload);
        lawyer.session().close();
    }

    @Test
    void 입장권이_없거나_이미_쓴_입장권이면_접속할_수_없다() throws Exception {
        assertThrows(ExecutionException.class, () -> connect("invalid", "http://localhost:3000"));

        String ticket = ticketService.issue(consultationId, SenderType.USER, userId);
        connect(ticket, "http://localhost:3000").session().close();
        assertThrows(ExecutionException.class, () -> connect(ticket, "http://localhost:3000"));
    }

    @Test
    void 허용되지_않은_사이트에서는_접속할_수_없다() {
        String ticket = ticketService.issue(consultationId, SenderType.USER, userId);
        assertThrows(ExecutionException.class, () -> connect(ticket, "http://evil.example.com"));
    }

    @Test
    void 대화방_당사자가_아니면_입장권을_받을_수_없다() {
        String otherToken = authService.signup("ws-other-" + UUID.randomUUID() + "@example.com", "password123").token();
        Long otherUserId = authService.validate(otherToken).orElseThrow().getId();

        assertThrows(ConsultationAccessDeniedException.class, () ->
                ticketService.issue(consultationId, SenderType.USER, otherUserId));
    }

    @Test
    void 변호사_목록_소켓은_새_문의와_새_메시지를_즉시_알린다() throws Exception {
        Connection inbox = connect("/ws/lawyer-inbox", ticketService.issueForInbox(SenderType.LAWYER, lawyerId), "http://localhost:3000");
        // 클라이언트 연결 완료와 서버의 세션 등록(afterConnectionEstablished) 사이에 틈이 있어, 등록을 기다린 뒤 알림을 만든다.
        // (PC가 바쁠 때 이 틈에 알림이 나가 사라져 간헐적으로 실패했다)
        awaitInboxRegistered(SenderType.LAWYER, lawyerId);

        Long newConsultation = consultationService.startConsultation(userId, "새 문의").getId();
        String created = inbox.received().poll(3, TimeUnit.SECONDS);
        assertNotNull(created, "새 문의가 배정되면 3초 안에 알림이 와야 한다");
        assertTrue(created.contains("\"type\":\"inbox\""), created);
        assertTrue(created.contains("NEW_CONSULTATION"), created);
        assertTrue(created.contains("\"consultationId\":" + newConsultation), created);

        consultationService.postMessage(consultationId, SenderType.USER, userId, "추가 질문");
        String posted = inbox.received().poll(3, TimeUnit.SECONDS);
        assertNotNull(posted);
        assertTrue(posted.contains("NEW_MESSAGE"), posted);
        assertTrue(posted.contains("\"consultationId\":" + consultationId), posted);
        inbox.session().close();
    }

    @Test
    void 다른_변호사의_목록_소켓에는_알림이_가지_않는다() throws Exception {
        Lawyer other = lawyerAuthService.signup("ws-other-lawyer-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "password123", "이변호", null, "67890",
                List.of(new MockMultipartFile("documents", "license.pdf", "application/pdf", "%PDF-1.4 dummy".getBytes())));
        Connection otherInbox = connect("/ws/lawyer-inbox", ticketService.issueForInbox(SenderType.LAWYER, other.getId()), "http://localhost:3000");
        awaitInboxRegistered(SenderType.LAWYER, other.getId()); // 등록 전이라 못 받은 것과 구분하려고 기다린다.

        consultationService.postMessage(consultationId, SenderType.USER, userId, "내 변호사에게만");

        assertNull(otherInbox.received().poll(1, TimeUnit.SECONDS));
        otherInbox.session().close();
    }

    @Test
    void 대화방_입장권과_목록_입장권은_서로_바꿔_쓸_수_없다() {
        String inboxTicket = ticketService.issueForInbox(SenderType.LAWYER, lawyerId);
        assertThrows(ExecutionException.class, () -> connect("/ws/consultations", inboxTicket, "http://localhost:3000"));

        String roomTicket = ticketService.issue(consultationId, SenderType.LAWYER, lawyerId);
        assertThrows(ExecutionException.class, () -> connect("/ws/lawyer-inbox", roomTicket, "http://localhost:3000"));
    }

    @Test
    void 회원_목록_소켓은_변호사가_답장하면_즉시_알린다() throws Exception {
        Connection inbox = connect("/ws/user-inbox", ticketService.issueForInbox(SenderType.USER, userId), "http://localhost:3000");
        awaitInboxRegistered(SenderType.USER, userId);

        consultationService.postMessage(consultationId, SenderType.LAWYER, lawyerId, "답변드립니다");

        String payload = inbox.received().poll(3, TimeUnit.SECONDS);
        assertNotNull(payload, "변호사가 답장하면 3초 안에 회원 목록 알림이 와야 한다");
        assertTrue(payload.contains("\"type\":\"inbox\""), payload);
        assertTrue(payload.contains("\"consultationId\":" + consultationId), payload);
        inbox.session().close();
    }

    @Test
    void 회원_목록_입장권과_변호사_목록_입장권은_서로_바꿔_쓸_수_없다() {
        String userInbox = ticketService.issueForInbox(SenderType.USER, userId);
        assertThrows(ExecutionException.class, () -> connect("/ws/lawyer-inbox", userInbox, "http://localhost:3000"));

        String lawyerInbox = ticketService.issueForInbox(SenderType.LAWYER, lawyerId);
        assertThrows(ExecutionException.class, () -> connect("/ws/user-inbox", lawyerInbox, "http://localhost:3000"));
    }
}

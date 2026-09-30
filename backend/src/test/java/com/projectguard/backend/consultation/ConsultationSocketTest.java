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
                List.of(new MockMultipartFile("documents", "license.pdf", "application/pdf", "dummy".getBytes())));
        lawyer.approve();
        lawyerRepository.save(lawyer);
        lawyerId = lawyer.getId();

        String token = authService.signup("ws-user-" + suffix + "@example.com", "password123").token();
        userId = authService.validate(token).orElseThrow().getId();
        consultationId = consultationService.startConsultation(userId, "첫 문의").getId();
    }

    private record Connection(WebSocketSession session, BlockingQueue<String> received) {
    }

    private Connection connect(String ticket, String origin) throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        WebSocketSession session = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                        received.add(message.getPayload());
                    }
                }, headers, URI.create("ws://localhost:" + port + "/ws/consultations?ticket=" + ticket))
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
}

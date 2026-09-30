package com.projectguard.backend.consultation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * ws://{백엔드}/ws/consultations?ticket=... 로 접속한다. 입장권(ConsultationSocketTicketService)이
 * 유효해야만 연결되며, 입장권에 묶인 대화방의 알림만 받는다. 다른 사이트가 사용자 브라우저로
 * 몰래 접속하지 못하도록 프론트엔드 주소(FRONTEND_ORIGIN)에서 온 연결만 허용한다.
 */
@Configuration
@EnableWebSocket
public class ConsultationSocketConfig implements WebSocketConfigurer {

    private final ConsultationSocketHandler handler;
    private final ConsultationSocketTicketService ticketService;
    private final String frontendOrigin;

    public ConsultationSocketConfig(
            ConsultationSocketHandler handler,
            ConsultationSocketTicketService ticketService,
            @Value("${FRONTEND_ORIGIN:http://localhost:3000}") String frontendOrigin
    ) {
        this.handler = handler;
        this.ticketService = ticketService;
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/consultations")
                .addInterceptors(new TicketHandshakeInterceptor())
                .setAllowedOrigins(frontendOrigin);
    }

    private class TicketHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes
        ) {
            String ticket = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
            return ticketService.consume(ticket)
                    .map(t -> {
                        attributes.put(ConsultationSocketHandler.CONSULTATION_ID_ATTRIBUTE, t.consultationId());
                        return true;
                    })
                    .orElseGet(() -> {
                        response.setStatusCode(HttpStatus.FORBIDDEN);
                        return false;
                    });
        }

        @Override
        public void afterHandshake(
                ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception
        ) {
        }
    }
}

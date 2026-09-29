package com.projectguard.backend.push;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PushSubscriptionController.class)
@TestPropertySource(properties = "push.vapid.public-key=test-public-key")
class PushSubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PushSubscriptionRepository subscriptionRepository;

    @MockitoBean
    private AuthService authService;

    @Test
    void 공개키_조회는_로그인_없이_가능하다() throws Exception {
        mockMvc.perform(get("/api/push/vapid-public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("test-public-key"));
    }

    @Test
    void 토큰이_없으면_구독등록은_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/push/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PushSubscriptionController.SubscribeRequest(
                                "https://example.com/endpoint", new PushSubscriptionController.SubscriptionKeys("p", "a")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인_상태에서_구독하면_저장된다() throws Exception {
        User user = new User("me@example.com", "hash");
        when(authService.validate("token-1")).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByEndpoint(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer token-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PushSubscriptionController.SubscribeRequest(
                                "https://example.com/endpoint", new PushSubscriptionController.SubscriptionKeys("p256dh-value", "auth-value")))))
                .andExpect(status().isOk());

        verify(subscriptionRepository).save(any());
    }

    @Test
    void 구독해지하면_리포지토리에서_삭제한다() throws Exception {
        when(authService.validate("token-2")).thenReturn(Optional.of(new User("me@example.com", "hash")));

        mockMvc.perform(delete("/api/push/subscriptions")
                        .header("Authorization", "Bearer token-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PushSubscriptionController.UnsubscribeRequest("https://example.com/endpoint"))))
                .andExpect(status().isOk());

        verify(subscriptionRepository).deleteByEndpoint("https://example.com/endpoint");
    }
}

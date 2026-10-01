package com.projectguard.backend.push;

import com.projectguard.backend.auth.AuthService;
import com.projectguard.backend.auth.User;
import com.projectguard.backend.consultation.SenderType;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerAuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceTokenController.class)
class DeviceTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeviceTokenRepository deviceTokenRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private LawyerAuthService lawyerAuthService;

    private String registerBody(String token) {
        return objectMapper.writeValueAsString(new DeviceTokenController.RegisterRequest(token, "android"));
    }

    @Test
    void 로그인하지_않으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/push/devices").contentType(MediaType.APPLICATION_JSON).content(registerBody("fcm-1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 회원_토큰으로_등록하면_회원_소유로_저장된다() throws Exception {
        User user = new User("me@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 7L);
        when(authService.validate("user-token")).thenReturn(Optional.of(user));
        when(deviceTokenRepository.findByToken("fcm-1")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/push/devices")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("fcm-1")))
                .andExpect(status().isOk());

        ArgumentCaptor<DeviceToken> saved = ArgumentCaptor.forClass(DeviceToken.class);
        verify(deviceTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getOwnerType()).isEqualTo(SenderType.USER);
        assertThat(saved.getValue().getOwnerId()).isEqualTo(7L);
    }

    @Test
    void 변호사_토큰으로_등록하면_같은_기기_토큰의_소유자가_변호사로_바뀐다() throws Exception {
        Lawyer lawyer = new Lawyer("lawyer@example.com", "hash", "김변호", null, "12345");
        ReflectionTestUtils.setField(lawyer, "id", 3L);
        when(authService.validate("lawyer-token")).thenReturn(Optional.empty());
        when(lawyerAuthService.validate("lawyer-token")).thenReturn(Optional.of(lawyer));
        DeviceToken existing = new DeviceToken(SenderType.USER, 7L, "fcm-1", "android");
        when(deviceTokenRepository.findByToken("fcm-1")).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/api/push/devices")
                        .header("Authorization", "Bearer lawyer-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("fcm-1")))
                .andExpect(status().isOk());

        assertThat(existing.getOwnerType()).isEqualTo(SenderType.LAWYER);
        assertThat(existing.getOwnerId()).isEqualTo(3L);
    }

    @Test
    void 빈_토큰은_400을_반환한다() throws Exception {
        when(authService.validate("user-token")).thenReturn(Optional.of(new User("me@example.com", "hash")));

        mockMvc.perform(post("/api/push/devices")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(" ")))
                .andExpect(status().isBadRequest());
        verify(deviceTokenRepository, never()).save(any());
    }

    @Test
    void 다른_계정의_토큰은_해지하지_않는다() throws Exception {
        User user = new User("me@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 7L);
        when(authService.validate("user-token")).thenReturn(Optional.of(user));
        when(deviceTokenRepository.findByToken(anyString()))
                .thenReturn(Optional.of(new DeviceToken(SenderType.USER, 8L, "fcm-other", "android")));

        mockMvc.perform(delete("/api/push/devices")
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceTokenController.UnregisterRequest("fcm-other"))))
                .andExpect(status().isOk());

        verify(deviceTokenRepository, never()).delete(any());
    }
}

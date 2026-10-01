package com.projectguard.backend.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Firebase Cloud Messaging(HTTP v1)으로 모바일 앱에 푸시를 보낸다. 서비스 계정 키(FCM_CREDENTIALS_PATH)가
 * 없으면 비활성화되고, 발송 실패는 로그만 남긴다 — 푸시가 안 돼도 상담은 항상 성공해야 한다.
 */
@Component
public class FcmClient {

    private static final Logger log = LoggerFactory.getLogger(FcmClient.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    /** 발송 결과. INVALID_TOKEN이면 앱이 지워졌거나 토큰이 바뀐 것이라 저장된 토큰을 지운다. */
    public enum Result { SENT, INVALID_TOKEN, FAILED }

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final GoogleCredentials credentials;
    private final String projectId;

    public FcmClient(ObjectMapper objectMapper, @Value("${push.fcm.credentials-path:}") String credentialsPath) {
        this.objectMapper = objectMapper;
        GoogleCredentials loaded = null;
        String loadedProjectId = null;
        if (credentialsPath.isBlank()) {
            log.warn("앱 푸시 설정(FCM_CREDENTIALS_PATH)이 없어 모바일 앱 푸시 알림이 비활성화됩니다.");
        } else {
            try (FileInputStream in = new FileInputStream(credentialsPath)) {
                loaded = GoogleCredentials.fromStream(in).createScoped(List.of(SCOPE));
                if (loaded instanceof ServiceAccountCredentials sa) {
                    loadedProjectId = sa.getProjectId();
                }
                if (loadedProjectId == null || loadedProjectId.isBlank()) {
                    log.warn("FCM 서비스 계정 키에 project_id가 없어 모바일 앱 푸시 알림이 비활성화됩니다.");
                    loaded = null;
                }
            } catch (Exception e) {
                log.warn("FCM 서비스 계정 키를 읽지 못해 모바일 앱 푸시 알림이 비활성화됩니다: {}", e.getMessage());
                loaded = null;
            }
        }
        this.credentials = loaded;
        this.projectId = loadedProjectId;
    }

    public boolean isEnabled() {
        return credentials != null;
    }

    /** 알림(제목·본문)과 앱이 탭 처리에 쓰는 data를 함께 보낸다. data 값은 모두 문자열이어야 한다. */
    public Result send(String token, String title, String body, Map<String, String> data) {
        if (credentials == null) {
            return Result.FAILED;
        }
        try {
            credentials.refreshIfExpired();
            Map<String, Object> message = Map.of("message", Map.of(
                    "token", token,
                    "notification", Map.of("title", title, "body", body),
                    "data", data,
                    "android", Map.of("priority", "HIGH"),
                    "apns", Map.of("payload", Map.of("aps", Map.of("sound", "default")))
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return Result.SENT;
            }
            // 404 UNREGISTERED: 앱 삭제·토큰 만료. 400 INVALID_ARGUMENT 중 토큰 형식 오류도 같은 처리.
            if (status == 404 || (status == 400 && response.body().contains("registration token"))) {
                return Result.INVALID_TOKEN;
            }
            log.warn("FCM 발송 실패 (HTTP {}): {}", status, response.body());
            return Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        } catch (Exception e) {
            log.warn("FCM 발송 중 오류: {}", e.getMessage());
            return Result.FAILED;
        }
    }
}

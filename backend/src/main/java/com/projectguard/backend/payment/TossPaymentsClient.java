package com.projectguard.backend.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 토스페이먼츠 결제 승인 API 호출. 브라우저 결제창에서 인증만 끝난 결제는 이 승인을 거쳐야 실제로
 * 돈이 빠져나간다. 시크릿 키(TOSS_SECRET_KEY)는 서버에만 두고, 비어 있으면 결제 기능 전체가 막힌다.
 */
@Component
public class TossPaymentsClient {

    private static final String CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String secretKey;

    public TossPaymentsClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${TOSS_SECRET_KEY:}") String secretKey
    ) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.secretKey = secretKey;
    }

    public void confirm(String paymentKey, String orderId, long amount) {
        if (secretKey.isBlank()) {
            throw new PaymentException("결제 설정이 되어 있지 않습니다. 관리자에게 문의해주세요.");
        }
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        try {
            restClient.post()
                    .uri(CONFIRM_URL)
                    .header("Authorization", "Basic " + basic)
                    // 같은 주문을 두 번 승인 요청해도 토스가 한 번만 처리하도록 주문번호를 멱등키로 쓴다.
                    .header("Idempotency-Key", orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new PaymentException(tossErrorMessage(e.getResponseBodyAsString()));
        } catch (RuntimeException e) {
            throw new PaymentException("결제 승인 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    /** 결제 전액 취소. 결제 취소(미사용 이용권)와 관리자 승인 환불에 모두 쓴다. */
    public void cancel(String paymentKey, String orderId, String reason) {
        if (secretKey.isBlank()) {
            throw new PaymentException("결제 설정이 되어 있지 않습니다. 관리자에게 문의해주세요.");
        }
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        try {
            restClient.post()
                    .uri("https://api.tosspayments.com/v1/payments/{paymentKey}/cancel", paymentKey)
                    .header("Authorization", "Basic " + basic)
                    .header("Idempotency-Key", "cancel-" + orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cancelReason", reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new PaymentException(tossErrorMessage(e.getResponseBodyAsString()));
        } catch (RuntimeException e) {
            throw new PaymentException("결제 취소 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private String tossErrorMessage(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String message = node.path("message").asString("");
            if (!message.isBlank()) {
                return message;
            }
        } catch (RuntimeException ignored) {
            // 응답 형식이 예상과 다르면 기본 문구를 쓴다.
        }
        return "결제 승인에 실패했습니다.";
    }
}

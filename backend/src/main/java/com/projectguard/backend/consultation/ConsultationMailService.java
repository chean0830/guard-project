package com.projectguard.backend.consultation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 새 상담 문의/메시지가 도착했음을 변호사 등록 이메일로 알린다. 메일 서버 설정이 없거나
 * 발송이 실패해도 상담 자체(문의 등록, 메시지 전송)는 정상적으로 이어져야 하므로,
 * 예외를 절대 밖으로 던지지 않고 로그만 남긴다 — 외부 API 실패에 대응하는 이 프로젝트의
 * 기존 원칙(시세 조회 등)과 동일하다.
 */
@Service
public class ConsultationMailService {

    private static final Logger log = LoggerFactory.getLogger(ConsultationMailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public ConsultationMailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendNewMessageNotification(String toEmail, String previewText) {
        if (fromAddress.isBlank()) {
            log.warn("메일 발송 설정(MAIL_USERNAME)이 없어 알림 메일을 건너뜁니다. 수신자: {}", toEmail);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject("[Project Guard] 새로운 상담 문의가 도착했습니다");
            message.setText(
                    "Project Guard에 새로운 상담 메시지가 도착했습니다.\n\n"
                            + previewText + "\n\n"
                            + "로그인 후 문의함에서 확인해주세요.\n"
                            + "이 알림을 받고 싶지 않다면 변호사 설정 화면에서 끌 수 있습니다."
            );
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("알림 메일 발송에 실패했습니다. 수신자: {}, 사유: {}", toEmail, e.getMessage());
        }
    }
}

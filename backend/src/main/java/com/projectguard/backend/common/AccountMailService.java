package com.projectguard.backend.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 계정 관련 안내 메일(비밀번호 재설정, 변호사 가입 승인/거절). ConsultationMailService와 같은 원칙으로
 * 메일 설정이 없거나 발송이 실패해도 예외를 밖으로 던지지 않는다 — 대신 보냈는지 여부를 돌려준다.
 */
@Service
public class AccountMailService {

    private static final Logger log = LoggerFactory.getLogger(AccountMailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public AccountMailService(JavaMailSender mailSender, @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public boolean isConfigured() {
        return !fromAddress.isBlank();
    }

    public boolean send(String to, String subject, String body) {
        if (!isConfigured()) {
            log.warn("메일 발송 설정(MAIL_USERNAME)이 없어 메일을 건너뜁니다. 수신자: {}, 제목: {}", to, subject);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject("[Project Guard] " + subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.warn("메일 발송에 실패했습니다. 수신자: {}, 사유: {}", to, e.getMessage());
            return false;
        }
    }
}

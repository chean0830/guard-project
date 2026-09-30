package com.projectguard.backend.consultation;

import java.time.Instant;

/** 상담 대화방에 새 메시지가 저장됐을 때 발행된다. ConsultationSocketHandler가 받아 대화방에 연결된 화면으로 즉시 보낸다. */
public record ConsultationMessagePostedEvent(Long consultationId, SenderType senderType, String content, Instant createdAt) {
}

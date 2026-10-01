package com.projectguard.backend.consultation;

/**
 * 회원·변호사의 상담 목록이 바뀌었을 때 발행된다(새 문의가 배정됐거나, 대화방에 새 메시지가 올라왔을 때).
 * InboxSocketHandler가 받아 그 사람(ownerType + ownerId)의 목록 화면으로 즉시 알린다.
 */
public record InboxChangedEvent(SenderType ownerType, Long ownerId, Long consultationId, Reason reason) {

    public enum Reason {
        NEW_CONSULTATION,
        NEW_MESSAGE
    }
}

package com.projectguard.backend.consultation;

/**
 * 변호사의 상담 목록이 바뀌었을 때 발행된다(새 문의가 배정됐거나, 대화방에 새 메시지가 올라왔을 때).
 * LawyerInboxSocketHandler가 받아 그 변호사의 목록 화면으로 즉시 알린다.
 */
public record LawyerInboxChangedEvent(Long lawyerId, Long consultationId, Reason reason) {

    public enum Reason {
        NEW_CONSULTATION,
        NEW_MESSAGE
    }
}

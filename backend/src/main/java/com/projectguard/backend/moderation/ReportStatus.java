package com.projectguard.backend.moderation;

public enum ReportStatus {
    /** 관리자 검토 전 */
    PENDING,
    /** 신고 기준 충족 → 신고 대상 계정을 정지함 */
    ACTIONED,
    /** 신고 기준 미충족 → 조치 없이 종료 */
    DISMISSED
}

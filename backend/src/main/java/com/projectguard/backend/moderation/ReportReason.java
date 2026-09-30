package com.projectguard.backend.moderation;

/**
 * 신고 사유. 상담 대화에서 제재 대상이 되는 행위를 욕설과 금전 요구 두 가지로 한정한다 —
 * "답변이 마음에 들지 않는다" 같은 불만성 신고까지 받으면 변호사가 부당하게 정지될 수 있어,
 * 관리자가 대화 원문만 보고 명확히 판단할 수 있는 기준만 남겼다.
 */
public enum ReportReason {
    ABUSIVE_LANGUAGE("욕설·모욕"),
    MONEY_REQUEST("금전 요구");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

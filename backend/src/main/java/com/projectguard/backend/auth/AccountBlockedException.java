package com.projectguard.backend.auth;

/** 신고 처리로 이용이 정지된 계정(회원/변호사 공통)이 로그인하려 할 때 던진다. */
public class AccountBlockedException extends RuntimeException {
    public AccountBlockedException(String reason) {
        super("신고 처리로 이용이 정지된 계정입니다." + (reason != null && !reason.isBlank() ? " 사유: " + reason : "")
                + " 문의가 필요하면 고객센터로 연락해주세요.");
    }
}

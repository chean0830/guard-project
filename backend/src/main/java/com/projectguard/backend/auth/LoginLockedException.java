package com.projectguard.backend.auth;

/** 비밀번호를 연속으로 틀려 잠긴 계정. 비밀번호 찾기(이메일 인증)로 새 비밀번호를 설정해야 풀린다. */
public class LoginLockedException extends RuntimeException {
    public static final int MAX_FAILED_ATTEMPTS = 5;

    public LoginLockedException() {
        super("비밀번호를 " + MAX_FAILED_ATTEMPTS + "회 틀려 로그인이 잠겼습니다. "
                + "'비밀번호 찾기'에서 이메일 인증 후 새 비밀번호를 설정하면 다시 로그인할 수 있어요.");
    }
}

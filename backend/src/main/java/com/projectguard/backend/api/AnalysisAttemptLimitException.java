package com.projectguard.backend.api;

/** 한 계정이 하루에 시도할 수 있는 분석 횟수를 넘었다. */
public class AnalysisAttemptLimitException extends RuntimeException {
    public AnalysisAttemptLimitException() {
        super("오늘 분석을 시도할 수 있는 횟수(" + AnalysisAccessService.ATTEMPTS_PER_DAY + "회)를 넘었어요. 내일 다시 시도해주세요.");
    }
}

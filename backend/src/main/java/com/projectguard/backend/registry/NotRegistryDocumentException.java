package com.projectguard.backend.registry;

/**
 * 업로드된 PDF에서 등기부등본(등기사항전부증명서)의 표준 제목을 찾지 못했을 때 발생한다.
 * 등기부등본이 아닌 다른 문서/사진을 올린 경우를 사용자에게 알려주기 위한 신호로 쓴다.
 */
public class NotRegistryDocumentException extends RuntimeException {

    public NotRegistryDocumentException(String message) {
        super(message);
    }
}

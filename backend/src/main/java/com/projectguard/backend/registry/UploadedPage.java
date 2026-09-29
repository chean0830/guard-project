package com.projectguard.backend.registry;

/**
 * 등기부등본 한 장(페이지)의 업로드 원본. PDF는 보통 한 파일에 모든 페이지가 들어있지만,
 * 카메라로 촬영한 경우 페이지마다 별도 이미지 파일이 되므로 여러 장을 리스트로 받는다.
 */
public record UploadedPage(byte[] bytes, String contentType) {

    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }
}

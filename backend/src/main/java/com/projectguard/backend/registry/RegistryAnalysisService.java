package com.projectguard.backend.registry;

import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class RegistryAnalysisService {

    private final RegistryPdfTextExtractor pdfTextExtractor;
    private final RegistryImageTextExtractor imageTextExtractor;
    private final RegistryParser parser;

    public RegistryAnalysisService(
            RegistryPdfTextExtractor pdfTextExtractor,
            RegistryImageTextExtractor imageTextExtractor,
            RegistryParser parser
    ) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.imageTextExtractor = imageTextExtractor;
        this.parser = parser;
    }

    /**
     * 업로드된 등기부등본 파일(PDF 또는 카메라 촬영 이미지) 바이트를 분석한다.
     * 원본 파일은 이 메서드 호출 전후로 디스크에 저장하지 않는다 (비저장 원칙).
     *
     * @param contentType 업로드된 파일의 MIME 타입. "image/"로 시작하면 Google Vision OCR을,
     *                    그 외(PDF 등)에는 PDFBox 텍스트 추출을 사용한다.
     */
    public RegistryAnalysis analyze(byte[] fileBytes, String contentType) throws IOException {
        boolean isImage = contentType != null && contentType.startsWith("image/");
        String rawText = isImage
                ? imageTextExtractor.extractText(fileBytes)
                : pdfTextExtractor.extractText(fileBytes);
        return parser.parse(rawText);
    }
}

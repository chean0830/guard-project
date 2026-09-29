package com.projectguard.backend.registry;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

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
     * 업로드된 등기부등본 페이지들(PDF 한 장 또는 촬영 이미지 여러 장)을 분석한다.
     * 각 페이지에서 추출한 텍스트를 업로드 순서대로 이어붙인 뒤 하나의 문서로 파싱한다 —
     * 표제부/갑구/을구가 페이지마다 나뉘어 촬영된 경우에도 순서대로 올리면 정상 인식된다.
     * 원본 파일은 이 메서드 호출 전후로 디스크에 저장하지 않는다 (비저장 원칙).
     */
    public RegistryAnalysis analyze(List<UploadedPage> pages) throws IOException {
        StringBuilder combinedText = new StringBuilder();
        for (UploadedPage page : pages) {
            String text = page.isImage()
                    ? imageTextExtractor.extractText(page.bytes())
                    : pdfTextExtractor.extractText(page.bytes());
            combinedText.append(text).append('\n');
        }
        return parser.parse(combinedText.toString());
    }
}

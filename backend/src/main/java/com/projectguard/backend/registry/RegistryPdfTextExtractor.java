package com.projectguard.backend.registry;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 등기부등본 PDF는 서브셋 임베딩 폰트 + Identity-H 인코딩을 쓰는 경우가 많아
 * 범용 도구(poppler 등)는 한글 추출에 실패한다. PDFBox는 ToUnicode CMap을 정상적으로
 * 해석해 추출 가능함을 샘플로 확인했다 (docs/결정사항.md 8번 참고).
 */
@Component
public class RegistryPdfTextExtractor {

    /** 등기부는 보통 3~5쪽이고 길어도 수십 쪽을 넘지 않는다. 엉뚱한 대용량 PDF로 서버를 붙잡는 것을 막는다. */
    static final int MAX_PAGES = 30;

    public String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw new NotRegistryDocumentException("PDF 쪽수가 너무 많아요 (" + MAX_PAGES + "쪽까지). 등기부등본 PDF만 올려주세요.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }
}

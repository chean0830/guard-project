package com.projectguard.backend.api;

import com.projectguard.backend.market.BuildingRegisterService;
import com.projectguard.backend.market.MarketPriceService;
import com.projectguard.backend.registry.NotRegistryDocumentException;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.registry.RegistryAnalysisService;
import com.projectguard.backend.risk.RiskAssessmentResult;
import com.projectguard.backend.risk.RiskAssessmentService;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import com.projectguard.backend.risk.RiskSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 외부 API(구글 비전, juso.go.kr, data.go.kr)를 실제로 호출하지 않도록 서비스 계층을
 * MockitoBean으로 대체하고, 컨트롤러의 요청 검증·조립·에러 응답만 검증한다.
 */
@WebMvcTest(AnalyzeController.class)
class AnalyzeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistryAnalysisService registryAnalysisService;

    @MockitoBean
    private MarketPriceService marketPriceService;

    @MockitoBean
    private BuildingRegisterService buildingRegisterService;

    @MockitoBean
    private RiskAssessmentService riskAssessmentService;

    private MockMultipartFile samplePdf() {
        return new MockMultipartFile("files", "test.pdf", "application/pdf", "dummy".getBytes());
    }

    @Test
    void 정상_요청이면_분석_결과를_반환한다() throws Exception {
        RegistryAnalysis registry = new RegistryAnalysis(
                "서울특별시 강남구 테스트로 123", "1234-2020-000001", List.of(), List.of(), List.of(), 0L);
        when(registryAnalysisService.analyze(anyList())).thenReturn(registry);
        when(marketPriceService.lookupMarketPrice(any(), any(), any(), any())).thenReturn(Optional.of(500_000_000L));

        RiskSignal signal = new RiskSignal(
                "HIGH_SENIOR_DEBT_RATIO", "선순위 채권 비율 높음", RiskSeverity.HIGH,
                RiskSource.GOVERNMENT_GUIDELINE, "국토교통부 안내자료", "위험합니다");
        when(riskAssessmentService.assess(any())).thenReturn(new RiskAssessmentResult(List.of(signal)));

        mockMvc.perform(multipart("/api/analyze")
                        .file(samplePdf())
                        .param("propertyType", "APARTMENT")
                        .param("depositAmount", "200000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registry.address").value("서울특별시 강남구 테스트로 123"))
                .andExpect(jsonPath("$.marketPrice").value(500_000_000))
                .andExpect(jsonPath("$.hasHighRisk").value(true))
                .andExpect(jsonPath("$.riskSignals[0].code").value("HIGH_SENIOR_DEBT_RATIO"))
                .andExpect(jsonPath("$.disclaimer").value("이 서비스는 법률 조언이 아니며 참고용 정보입니다."));
    }

    @Test
    void 파일이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(multipart("/api/analyze")
                        .param("propertyType", "APARTMENT")
                        .param("depositAmount", "200000000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 등기부등본이_아니면_400과_안내메시지를_반환한다() throws Exception {
        when(registryAnalysisService.analyze(anyList()))
                .thenThrow(new NotRegistryDocumentException("등기부등본(등기사항전부증명서)이 아닌 것 같습니다. 등기부등본 PDF를 업로드해주세요."));

        mockMvc.perform(multipart("/api/analyze")
                        .file(samplePdf())
                        .param("propertyType", "APARTMENT")
                        .param("depositAmount", "200000000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("등기부등본(등기사항전부증명서)이 아닌 것 같습니다. 등기부등본 PDF를 업로드해주세요."));
    }

    @Test
    void 파일을_읽을_수_없으면_400을_반환한다() throws Exception {
        when(registryAnalysisService.analyze(anyList())).thenThrow(new java.io.IOException("broken"));

        mockMvc.perform(multipart("/api/analyze")
                        .file(samplePdf())
                        .param("propertyType", "APARTMENT")
                        .param("depositAmount", "200000000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("파일을 처리할 수 없습니다. 등기부등본 PDF 또는 촬영 사진을 업로드해주세요."));
    }

    @Test
    void 부동산_유형이_잘못되면_400을_반환한다() throws Exception {
        mockMvc.perform(multipart("/api/analyze")
                        .file(samplePdf())
                        .param("propertyType", "STUDIO")
                        .param("depositAmount", "200000000"))
                .andExpect(status().isBadRequest());
    }
}

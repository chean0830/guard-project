package com.projectguard.backend.api;

import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.market.MarketPriceService;
import com.projectguard.backend.registry.NotRegistryDocumentException;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.registry.RegistryAnalysisService;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskAssessmentResult;
import com.projectguard.backend.risk.RiskAssessmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 등기부등본 업로드(PDF 또는 카메라 촬영 이미지) → 파싱 → 시세 조회 → 위험 판단을 한 번에 처리하는 엔드포인트.
 * 업로드된 원본 파일은 메모리에서만 처리하고 디스크에 저장하지 않는다 (비저장 원칙).
 */
@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private static final String DISCLAIMER = "이 서비스는 법률 조언이 아니며 참고용 정보입니다.";

    private final RegistryAnalysisService registryAnalysisService;
    private final MarketPriceService marketPriceService;
    private final RiskAssessmentService riskAssessmentService;

    public AnalyzeController(
            RegistryAnalysisService registryAnalysisService,
            MarketPriceService marketPriceService,
            RiskAssessmentService riskAssessmentService
    ) {
        this.registryAnalysisService = registryAnalysisService;
        this.marketPriceService = marketPriceService;
        this.riskAssessmentService = riskAssessmentService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalyzeResponse analyze(
            @RequestParam("file") MultipartFile file,
            @RequestParam("propertyType") PropertyType propertyType,
            @RequestParam("depositAmount") long depositAmount,
            @RequestParam(value = "buildingName", required = false) String buildingName,
            @RequestParam(value = "exclusiveAreaSqm", required = false) Double exclusiveAreaSqm,
            @RequestParam(value = "declaredLandlordName", required = false) String declaredLandlordName
    ) throws IOException {
        RegistryAnalysis registry = registryAnalysisService.analyze(file.getBytes(), file.getContentType());

        Long marketPrice = marketPriceService
                .lookupMarketPrice(propertyType, registry.address(), buildingName, exclusiveAreaSqm)
                .orElse(null);

        RiskAssessmentResult result = riskAssessmentService.assess(
                new RiskAssessmentInput(registry, depositAmount, marketPrice, declaredLandlordName));

        return new AnalyzeResponse(registry, marketPrice, result.signals(), result.hasHighRisk(), DISCLAIMER);
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleUnreadableFile(IOException e) {
        return "파일을 처리할 수 없습니다. 등기부등본 PDF 또는 촬영 사진을 업로드해주세요.";
    }

    @ExceptionHandler(NotRegistryDocumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleNotRegistryDocument(NotRegistryDocumentException e) {
        return e.getMessage();
    }
}

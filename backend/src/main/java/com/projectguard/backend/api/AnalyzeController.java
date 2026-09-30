package com.projectguard.backend.api;

import com.projectguard.backend.checklist.ChecklistItem;
import com.projectguard.backend.checklist.ChecklistService;
import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.market.BuildingInfo;
import com.projectguard.backend.market.BuildingRegisterService;
import com.projectguard.backend.market.MarketPriceService;
import com.projectguard.backend.registry.NotRegistryDocumentException;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.registry.RegistryAnalysisService;
import com.projectguard.backend.registry.UploadedPage;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskAssessmentResult;
import com.projectguard.backend.risk.RiskAssessmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 등기부등본 업로드(PDF 한 장 또는 카메라 촬영 이미지 여러 장) → 파싱 → 시세 조회 → 위험 판단을
 * 한 번에 처리하는 엔드포인트. 업로드된 원본 파일은 메모리에서만 처리하고 디스크에 저장하지 않는다 (비저장 원칙).
 * 프론트엔드(Next.js)는 브라우저에서 이 엔드포인트를 직접 호출하지 않고, 서버 사이드(Server Action)에서
 * 프록시로 호출한다 — CORS 설정이 필요 없고 백엔드 주소를 클라이언트에 노출하지 않는다.
 */
@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private final AnalysisAccessService analysisAccessService;

    private static final String DISCLAIMER = "이 서비스는 법률 조언이 아니며 참고용 정보입니다.";

    private final RegistryAnalysisService registryAnalysisService;
    private final MarketPriceService marketPriceService;
    private final BuildingRegisterService buildingRegisterService;
    private final RiskAssessmentService riskAssessmentService;
    private final ChecklistService checklistService;

    public AnalyzeController(
            RegistryAnalysisService registryAnalysisService,
            MarketPriceService marketPriceService,
            BuildingRegisterService buildingRegisterService,
            RiskAssessmentService riskAssessmentService,
            ChecklistService checklistService,
            AnalysisAccessService analysisAccessService
    ) {
        this.registryAnalysisService = registryAnalysisService;
        this.marketPriceService = marketPriceService;
        this.buildingRegisterService = buildingRegisterService;
        this.riskAssessmentService = riskAssessmentService;
        this.checklistService = checklistService;
        this.analysisAccessService = analysisAccessService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalyzeResponse analyze(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("propertyType") PropertyType propertyType,
            @RequestParam("contractType") ContractType contractType,
            @RequestParam("depositAmount") long depositAmount,
            @RequestParam(value = "monthlyRent", required = false) Long monthlyRent,
            @RequestParam(value = "buildingName", required = false) String buildingName,
            @RequestParam(value = "exclusiveAreaSqm", required = false) Double exclusiveAreaSqm,
            @RequestParam(value = "declaredLandlordName", required = false) String declaredLandlordName,
            @RequestParam(value = "declaredAddress", required = false) String declaredAddress,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new NotRegistryDocumentException("등기부등본 파일을 1장 이상 업로드해주세요.");
        }
        AnalysisAccessService.Access access = analysisAccessService.authorize(bearer(authorization));

        List<UploadedPage> pages = new ArrayList<>();
        for (MultipartFile file : files) {
            pages.add(new UploadedPage(file.getBytes(), file.getContentType()));
        }

        RegistryAnalysis registry = registryAnalysisService.analyze(pages);

        Long marketPrice = marketPriceService
                .lookupMarketPrice(propertyType, registry.address(), buildingName, exclusiveAreaSqm)
                .orElse(null);

        BuildingInfo buildingInfo = buildingRegisterService.lookup(registry.address()).orElse(null);

        RiskAssessmentResult result = riskAssessmentService.assess(new RiskAssessmentInput(
                registry, contractType, depositAmount, monthlyRent, marketPrice, declaredLandlordName, declaredAddress));

        List<ChecklistItem> checklist = checklistService.generate(registry, contractType);

        analysisAccessService.complete(access);
        return new AnalyzeResponse(
                registry, marketPrice, buildingInfo, result.signals(), result.hasHighRisk(), checklist, DISCLAIMER);
    }

    private static String bearer(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
    }

    @ExceptionHandler(com.projectguard.backend.auth.InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleLoginRequired(com.projectguard.backend.auth.InvalidCredentialsException e) {
        return e.getMessage();
    }

    @ExceptionHandler(AnalysisPaymentRequiredException.class)
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    public String handlePaymentRequired(AnalysisPaymentRequiredException e) {
        return e.getMessage();
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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public String handleTooLarge(MaxUploadSizeExceededException e) {
        return "파일 용량이 너무 큽니다. 파일 한 장당 20MB, 전체 100MB 이하로 올려주세요.";
    }
}

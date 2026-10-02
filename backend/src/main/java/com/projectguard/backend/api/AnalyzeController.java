package com.projectguard.backend.api;

import com.projectguard.backend.checklist.ChecklistItem;
import com.projectguard.backend.checklist.ChecklistService;
import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.common.PriorDepositSource;
import com.projectguard.backend.common.ViolationBuildingAnswer;
import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.market.BuildingInfo;
import com.projectguard.backend.market.BuildingRegisterService;
import com.projectguard.backend.market.MarketPriceService;
import com.projectguard.backend.market.OfficialHousePrice;
import com.projectguard.backend.market.OfficialHousePriceService;
import com.projectguard.backend.registry.NotRegistryDocumentException;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.registry.RegistryAnalysisService;
import com.projectguard.backend.registry.RegistryKind;
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

    private static final String MULTI_HOUSEHOLD_DISCLAIMER = DISCLAIMER
            + " 다가구주택은 먼저 들어온 세입자 보증금이 등기부에 나오지 않아, 이 결과는 직접 입력하신 선순위 보증금과"
            + " 건물 시세가 정확하다는 전제에서만 의미가 있습니다. 서비스는 입력값을 확인하지 않으며, 안전하다고 판정하지 않습니다.";

    private final RegistryAnalysisService registryAnalysisService;
    private final MarketPriceService marketPriceService;
    private final BuildingRegisterService buildingRegisterService;
    private final RiskAssessmentService riskAssessmentService;
    private final ChecklistService checklistService;
    private final OfficialHousePriceService officialHousePriceService;

    public AnalyzeController(
            RegistryAnalysisService registryAnalysisService,
            MarketPriceService marketPriceService,
            BuildingRegisterService buildingRegisterService,
            RiskAssessmentService riskAssessmentService,
            ChecklistService checklistService,
            AnalysisAccessService analysisAccessService,
            OfficialHousePriceService officialHousePriceService
    ) {
        this.registryAnalysisService = registryAnalysisService;
        this.marketPriceService = marketPriceService;
        this.buildingRegisterService = buildingRegisterService;
        this.riskAssessmentService = riskAssessmentService;
        this.checklistService = checklistService;
        this.analysisAccessService = analysisAccessService;
        this.officialHousePriceService = officialHousePriceService;
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
            @RequestParam(value = "priorDepositTotal", required = false) Long priorDepositTotal,
            @RequestParam(value = "priorDepositSource", required = false) PriorDepositSource priorDepositSource,
            @RequestParam(value = "buildingPrice", required = false) Long buildingPrice,
            @RequestParam(value = "violationBuilding", required = false) ViolationBuildingAnswer violationBuilding,
            @RequestParam(value = "landFiles", required = false) List<MultipartFile> landFiles,
            @RequestParam(value = "roomCount", required = false) Integer roomCount,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new NotRegistryDocumentException("등기부등본 파일을 1장 이상 업로드해주세요.");
        }
        AnalysisAccessService.Access access = analysisAccessService.authorize(bearer(authorization));

        boolean multiHousehold = propertyType == PropertyType.MULTI_HOUSEHOLD;
        List<UploadedPage> pages = toPages(files);

        RegistryAnalysis registry = registryAnalysisService.analyze(pages);

        // 다가구주택은 토지 등기부를 함께 올릴 수 있다 (토지에만 걸린 근저당·압류 확인용).
        RegistryAnalysis landRegistry = multiHouseholdLandRegistry(propertyType, landFiles);

        // 다가구주택은 시세를 자동 조회하지 않고 사용자가 입력한 건물 전체 시세를 쓴다 (MarketPriceService 참고).
        Long marketPrice = multiHousehold
                ? buildingPrice
                : marketPriceService
                        .lookupMarketPrice(propertyType, registry.address(), buildingName, exclusiveAreaSqm)
                        .orElse(null);

        BuildingInfo buildingInfo = buildingRegisterService.lookup(registry.address()).orElse(null);

        // 공시가격(개별주택가격)은 단독·다가구주택에만 있다.
        OfficialHousePrice officialHousePrice = multiHousehold
                ? officialHousePriceService.lookup(registry.address()).orElse(null)
                : null;

        RiskAssessmentResult result = riskAssessmentService.assess(new RiskAssessmentInput(
                registry, contractType, depositAmount, monthlyRent, marketPrice, declaredLandlordName, declaredAddress,
                propertyType,
                multiHousehold ? priorDepositTotal : null,
                multiHousehold ? priorDepositSource : null,
                buildingInfo,
                violationBuilding,
                officialHousePrice,
                landRegistry,
                multiHousehold ? roomCount : null));

        List<ChecklistItem> checklist = checklistService.generate(
                registry, contractType, propertyType, violationBuilding, landRegistry != null);

        analysisAccessService.complete(access);
        return new AnalyzeResponse(
                propertyType, registry, landRegistry, marketPrice, officialHousePrice, buildingInfo, result.signals(),
                result.hasHighRisk(), checklist,
                multiHousehold ? MULTI_HOUSEHOLD_DISCLAIMER : DISCLAIMER);
    }

    private List<UploadedPage> toPages(List<MultipartFile> files) throws IOException {
        List<UploadedPage> pages = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            pages.add(new UploadedPage(file.getBytes(), file.getContentType()));
        }
        return pages;
    }

    private RegistryAnalysis multiHouseholdLandRegistry(PropertyType propertyType, List<MultipartFile> landFiles)
            throws IOException {
        if (propertyType != PropertyType.MULTI_HOUSEHOLD || landFiles == null) {
            return null;
        }
        List<UploadedPage> landPages = toPages(landFiles);
        if (landPages.isEmpty()) {
            return null;
        }
        RegistryAnalysis land;
        try {
            land = registryAnalysisService.analyze(landPages);
        } catch (NotRegistryDocumentException e) {
            throw new NotRegistryDocumentException("토지 등기부등본으로 올리신 파일이 등기부등본이 아닌 것 같습니다. 토지 등기부등본 PDF를 올려주세요.");
        }
        if (land.registryKind() == RegistryKind.BUILDING || land.registryKind() == RegistryKind.COLLECTIVE_BUILDING) {
            throw new NotRegistryDocumentException("토지 등기부등본 칸에 건물 등기부등본을 올리셨어요. 인터넷등기소에서 '토지' 등기부등본을 발급받아 올려주세요.");
        }
        return land;
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

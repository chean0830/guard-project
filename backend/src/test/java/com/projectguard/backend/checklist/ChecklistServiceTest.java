package com.projectguard.backend.checklist;

import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.common.ViolationBuildingAnswer;
import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.registry.MortgageEntry;
import com.projectguard.backend.registry.RegistryAnalysis;
import com.projectguard.backend.registry.SeizureEntry;
import com.projectguard.backend.registry.SeizureType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecklistServiceTest {

    private final ChecklistService service = new ChecklistService();

    private RegistryAnalysis emptyRegistry() {
        return new RegistryAnalysis("주소", "고유번호", List.of(), List.of(), List.of(), 0L);
    }

    private boolean hasItem(List<ChecklistItem> items, String title) {
        return items.stream().anyMatch(i -> i.title().equals(title));
    }

    @Test
    void 전세는_보증금반환보증_안내가_포함되고_월세_전용_항목은_없다() {
        List<ChecklistItem> items = service.generate(emptyRegistry(), ContractType.JEONSE);

        assertTrue(hasItem(items, "전세보증금 반환보증 가입 가능 여부"));
        assertFalse(hasItem(items, "관리비 항목 확인"));
        assertFalse(hasItem(items, "전월세전환율 확인"));
    }

    @Test
    void 월세는_관리비와_전환율_안내가_포함되고_전세_전용_항목은_없다() {
        List<ChecklistItem> items = service.generate(emptyRegistry(), ContractType.WOLSE);

        assertTrue(hasItem(items, "관리비 항목 확인"));
        assertTrue(hasItem(items, "전월세전환율 확인"));
        assertFalse(hasItem(items, "전세보증금 반환보증 가입 가능 여부"));
    }

    @Test
    void 근저당이_있으면_선순위채권_확인_항목이_추가된다() {
        RegistryAnalysis registry = new RegistryAnalysis(
                "주소", "고유번호", List.of(),
                List.of(new MortgageEntry(1, 100_000_000L, "홍길동", "테스트은행", "2020년1월1일", false)),
                List.of(), 100_000_000L);

        List<ChecklistItem> items = service.generate(registry, ContractType.JEONSE);

        assertTrue(hasItem(items, "선순위 채권 잔액 확인"));
    }

    @Test
    void 압류가_있으면_압류_확인_항목이_추가된다() {
        RegistryAnalysis registry = new RegistryAnalysis(
                "주소", "고유번호", List.of(), List.of(),
                List.of(new SeizureEntry(1, SeizureType.SEIZURE, "2020년1월1일", false)), 0L);

        List<ChecklistItem> items = service.generate(registry, ContractType.JEONSE);

        assertTrue(hasItem(items, "압류·가처분 해소 여부 확인"));
    }

    @Test
    void 근저당_압류가_없으면_해당_항목이_없다() {
        List<ChecklistItem> items = service.generate(emptyRegistry(), ContractType.JEONSE);

        assertFalse(hasItem(items, "선순위 채권 잔액 확인"));
        assertFalse(hasItem(items, "압류·가처분 해소 여부 확인"));
    }

    @Test
    void 다가구주택이면_확정일자_부여현황과_토지_등기부_확인_항목이_추가된다() {
        List<ChecklistItem> items = service.generate(emptyRegistry(), ContractType.JEONSE, PropertyType.MULTI_HOUSEHOLD);

        assertTrue(hasItem(items, "확정일자 부여현황 확인 (다가구 필수)"));
        assertTrue(hasItem(items, "토지 등기부등본 확인 (다가구 필수)"));
        assertFalse(hasItem(service.generate(emptyRegistry(), ContractType.JEONSE), "토지 등기부등본 확인 (다가구 필수)"));
    }

    @Test
    void 위반건축물_표시가_없다고_확인했을_때만_확인_항목을_뺀다() {
        String title = "건축물대장 위반건축물 표시 확인";
        assertTrue(hasItem(service.generate(emptyRegistry(), ContractType.JEONSE), title));
        assertTrue(hasItem(service.generate(
                emptyRegistry(), ContractType.JEONSE, PropertyType.VILLA, ViolationBuildingAnswer.UNKNOWN), title));
        assertTrue(hasItem(service.generate(
                emptyRegistry(), ContractType.JEONSE, PropertyType.VILLA, ViolationBuildingAnswer.MARKED), title));
        assertFalse(hasItem(service.generate(
                emptyRegistry(), ContractType.JEONSE, PropertyType.VILLA, ViolationBuildingAnswer.NOT_MARKED), title));
    }
}

package com.projectguard.backend.checklist;

import com.projectguard.backend.common.ContractType;
import com.projectguard.backend.common.PropertyType;
import com.projectguard.backend.common.ViolationBuildingAnswer;
import com.projectguard.backend.registry.RegistryAnalysis;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 등기부등본 분석만으로는 확인할 수 없는 항목을 할 일 목록으로 안내한다 (docs/기획서.md 2번 P0).
 * 전세/월세 계약 형태에 따라, 그리고 실제 분석 결과(근저당·압류 존재 여부)에 따라 안내 항목이 달라진다.
 */
@Service
public class ChecklistService {

    public List<ChecklistItem> generate(RegistryAnalysis registry, ContractType contractType) {
        return generate(registry, contractType, PropertyType.APARTMENT);
    }

    public List<ChecklistItem> generate(RegistryAnalysis registry, ContractType contractType, PropertyType propertyType) {
        return generate(registry, contractType, propertyType, null);
    }

    public List<ChecklistItem> generate(
            RegistryAnalysis registry, ContractType contractType, PropertyType propertyType,
            ViolationBuildingAnswer violationBuilding
    ) {
        return generate(registry, contractType, propertyType, violationBuilding, false);
    }

    /**
     * @param landRegistryProvided 토지 등기부를 함께 올렸는지. 올렸으면 "토지 등기부 확인" 안내는 뺀다.
     */
    public List<ChecklistItem> generate(
            RegistryAnalysis registry, ContractType contractType, PropertyType propertyType,
            ViolationBuildingAnswer violationBuilding, boolean landRegistryProvided
    ) {
        List<ChecklistItem> items = new ArrayList<>();

        if (propertyType == PropertyType.MULTI_HOUSEHOLD) {
            items.add(new ChecklistItem(
                    "확정일자 부여현황 확인 (다가구 필수)",
                    "임대인 동의를 받아 주민센터에서 이 건물의 확정일자 부여현황을 발급받으세요. 먼저 들어온 세입자들의 보증금과 확정일자가 나와요. 임대인 말만 믿지 마세요."));
            if (!landRegistryProvided) {
                items.add(new ChecklistItem(
                        "토지 등기부등본 확인 (다가구 필수)",
                        "다가구주택은 건물과 토지 등기부가 따로 있어요. 토지에만 걸린 근저당이나 압류가 있는지 토지 등기부도 꼭 열람하세요."));
            }
            items.add(new ChecklistItem(
                    "계약서에 호수와 선순위 보증금 명시",
                    "다가구는 호수가 등기부에 없어요. 계약서에 건물 전체 주소와 내가 쓰는 호수(층·위치)를 적고, 임대인이 알려준 선순위 보증금 합계도 특약으로 적어두세요."));
        }

        items.add(new ChecklistItem(
                "임대인 신분증 확인",
                "계약 전 임대인 신분증과 등기부등본 소유자 정보가 일치하는지 확인하세요. 대리인이 나왔다면 위임장과 인감증명서도 함께 확인하세요."));
        items.add(new ChecklistItem(
                "전입세대 열람",
                "주민센터에서 전입세대 열람내역서를 발급받아, 이미 다른 세입자가 전입해 있지는 않은지 확인하세요."));
        items.add(new ChecklistItem(
                "계약 당일 등기부등본 재열람",
                "계약금을 보내기 직전 등기부등본을 다시 한 번 열람해, 그 사이 근저당이나 압류가 새로 생기지 않았는지 확인하세요."));
        items.add(new ChecklistItem(
                "전입신고 + 확정일자",
                "이사 당일 바로 전입신고를 하고 확정일자를 받아 대항력과 우선변제권을 확보하세요."));

        // 위반건축물 여부는 공공 API로 알 수 없어, 표시가 없다고 직접 확인한 경우가 아니면 확인 방법을 안내한다.
        if (violationBuilding != ViolationBuildingAnswer.NOT_MARKED) {
            items.add(new ChecklistItem(
                    "건축물대장 위반건축물 표시 확인",
                    "정부24에서 건축물대장(일반 또는 집합)을 무료로 열람해, 첫 장 위쪽에 '위반건축물' 표시가 있는지 확인하세요. 표시가 있으면 전세자금대출·보증보험 가입이 막힐 수 있어요."));
        }

        if (!registry.mortgages().isEmpty()) {
            items.add(new ChecklistItem(
                    "선순위 채권 잔액 확인",
                    "등기부상 채권최고액은 실제 대출 잔액과 다를 수 있어요. 임대인에게 대출 상환 계획이나 잔액 증명서를 요청해보세요."));
        }

        if (!registry.seizures().isEmpty()) {
            items.add(new ChecklistItem(
                    "압류·가처분 해소 여부 확인",
                    "말소되지 않은 압류·가처분이 있다면, 계약 전 해당 채권자와 임대인 사이에 어떻게 해결되고 있는지 확인하세요."));
        }

        if (contractType == ContractType.JEONSE) {
            items.add(new ChecklistItem(
                    "전세보증금 반환보증 가입 가능 여부",
                    "HUG(주택도시보증공사) 또는 SGI서울보증의 전세보증금 반환보증에 가입할 수 있는 집인지 계약 전 미리 확인하세요."));
        } else {
            items.add(new ChecklistItem(
                    "관리비 항목 확인",
                    "관리비에 전기·수도·인터넷 등 어떤 항목이 포함되는지 계약서에 명시해달라고 요청하세요."));
            items.add(new ChecklistItem(
                    "전월세전환율 확인",
                    "월세가 법정 전환율 상한(기준금리+2%p, 연 10% 중 낮은 값)을 넘지 않는지 확인하세요."));
        }

        return items;
    }
}

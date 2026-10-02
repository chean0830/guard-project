package com.projectguard.backend.risk.rules;

import com.projectguard.backend.registry.RegistryKind;
import com.projectguard.backend.risk.RiskAssessmentInput;
import com.projectguard.backend.risk.RiskRule;
import com.projectguard.backend.risk.RiskSeverity;
import com.projectguard.backend.risk.RiskSignal;
import com.projectguard.backend.risk.RiskSource;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 사용자가 고른 부동산 유형과 업로드한 등기부 종류가 맞는지 확인한다. 유형에 따라 판정 기준이 달라서
 * (집합건물은 호실 시세·전세가율, 다가구는 선순위 보증금), 잘못 고르면 결과를 믿을 수 없다.
 *  - 아파트·오피스텔·빌라를 골랐는데 "[건물]" 등기부: 다가구일 수 있어 선순위 보증금 확인이 빠진 결과 → HIGH
 *  - 다가구를 골랐는데 "[집합건물]" 등기부: 호실 시세 비교가 빠진 결과 → HIGH
 *  - "[토지]" 등기부: 건물 권리관계가 아예 없음 → HIGH
 * 등기부 종류를 알아내지 못했으면(촬영 이미지에서 첫 페이지가 빠진 경우 등) 판단하지 않는다.
 */
@Component
public class PropertyTypeMismatchRule implements RiskRule {

    private static final String CODE = "PROPERTY_TYPE_MISMATCH";
    private static final String SOURCE_DESCRIPTION = "등기부등본 첫 줄의 등기부 종류 표시([집합건물]/[건물]/[토지])";

    @Override
    public Optional<RiskSignal> evaluate(RiskAssessmentInput input) {
        RegistryKind kind = input.registry().registryKind();
        if (kind == null || kind == RegistryKind.UNKNOWN) {
            return Optional.empty();
        }

        if (kind == RegistryKind.LAND) {
            return Optional.of(signal(
                    "올리신 서류가 토지 등기부예요",
                    "토지 등기부에는 건물의 소유자·근저당이 나오지 않아 이 결과로 집의 위험을 판단할 수 없어요. "
                            + "인터넷등기소에서 '건물'(아파트·빌라·오피스텔은 '집합건물') 등기부를 발급받아 다시 분석해주세요. "
                            + "다가구주택이라면 토지 등기부는 건물 등기부와 함께 따로 확인하시면 돼요."));
        }

        if (input.isMultiHousehold() && kind == RegistryKind.COLLECTIVE_BUILDING) {
            return Optional.of(signal(
                    "다가구주택을 고르셨는데 집합건물 등기부예요",
                    "등기부 첫 줄이 [집합건물]이면 호실마다 등기부가 따로 있는 아파트·오피스텔·빌라(연립·다세대)예요. "
                            + "원룸이라도 이 경우는 다가구가 아니에요. 다가구 기준으로 판단하면 호실 시세 비교가 빠지니, "
                            + "부동산 유형을 빌라·오피스텔·아파트 중에서 다시 골라 분석해주세요."));
        }

        if (!input.isMultiHousehold() && kind == RegistryKind.BUILDING) {
            return Optional.of(signal(
                    "고르신 유형과 달리 단독·다가구주택 등기부예요",
                    "등기부 첫 줄이 [건물]이면 건물 전체에 등기부가 하나뿐인 단독·다가구주택이에요. "
                            + "다가구라면 나보다 먼저 들어온 세입자들의 보증금이 먼저 배당되는데, 지금 결과에는 이 확인이 빠져 있어요. "
                            + "부동산 유형을 '원룸·다가구주택'으로 바꿔 다시 분석해주세요."));
        }

        return Optional.empty();
    }

    private RiskSignal signal(String title, String detail) {
        return new RiskSignal(CODE, title, RiskSeverity.HIGH, RiskSource.FACTUAL, SOURCE_DESCRIPTION, detail);
    }
}

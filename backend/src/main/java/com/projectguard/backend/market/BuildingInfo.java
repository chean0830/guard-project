package com.projectguard.backend.market;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 건축물대장 표제부(getBrTitleInfo)·층별개요(getBrFlrOulnInfo) 조회 결과 중 사용자에게 보여줄 핵심 정보.
 *
 * 용도는 이름보다 코드로 판별한다 — 층별개요의 용도 이름은 "의원", "학원"처럼 세부 용도만 오는 경우가 많다
 * (실제 응답으로 확인). 코드 앞 두 자리: 01 단독주택, 02 공동주택, 03 제1종근린생활시설, 04 제2종근린생활시설.
 *
 * @param mainPurposeCode 표제부 주용도 코드 (예: "03000")
 * @param etcPurpose      표제부 기타용도 (예: "다가구주택(5가구)", "점포, 주택")
 * @param floors          층별 용도. 층별개요 조회에 실패하면 빈 목록 — 층 단위 판단은 이때 건너뛴다.
 * @param familyCount     표제부 가구수(fmlyCnt). 다가구주택의 방(가구) 수로, 소액임차인 최우선변제 추정에 쓴다.
 */
public record BuildingInfo(
        String buildingName,
        String mainPurpose,
        String structureType,
        String useApprovalDate,
        Double totalFloorAreaSqm,
        String mainPurposeCode,
        String etcPurpose,
        List<FloorUse> floors,
        Integer familyCount
) {

    public BuildingInfo(
            String buildingName,
            String mainPurpose,
            String structureType,
            String useApprovalDate,
            Double totalFloorAreaSqm,
            String mainPurposeCode,
            String etcPurpose,
            List<FloorUse> floors
    ) {
        this(buildingName, mainPurpose, structureType, useApprovalDate, totalFloorAreaSqm, mainPurposeCode,
                etcPurpose, floors, null);
    }

    /**
     * @param underground 지하층 여부
     * @param floorNo     층 번호 (정렬용, 모르면 0)
     * @param floorName   층 이름 (예: "지1", "1층")
     * @param purposeCode 주용도 코드 (예: "04010")
     * @param mainPurpose 주용도 이름 (예: "학원")
     * @param etcPurpose  기타용도 (예: "제2종근린생활시설(학원)")
     */
    public record FloorUse(
            boolean underground,
            int floorNo,
            String floorName,
            String purposeCode,
            String mainPurpose,
            String etcPurpose
    ) {

        public boolean isNeighborhoodFacility() {
            return isNeighborhoodFacilityPurpose(purposeCode, mainPurpose);
        }

        public boolean isHousing() {
            return isHousingPurpose(purposeCode, mainPurpose);
        }

        /** 옥탑(계단실·기계실 등)은 사람이 사는 층이 아니라 판단에서 뺀다. */
        boolean isRooftop() {
            return floorName != null && floorName.contains("옥탑");
        }

        String purposeLabel() {
            return etcPurpose != null && !etcPurpose.isBlank() ? etcPurpose : mainPurpose;
        }
    }

    /** 건물 주용도가 근린생활시설인지. */
    public boolean isNeighborhoodFacilityBuilding() {
        return isNeighborhoodFacilityPurpose(mainPurposeCode, mainPurpose);
    }

    /** 층별개요를 조회했는지. 조회 실패면 층 단위 판단을 하지 않는다. */
    public boolean hasFloorInfo() {
        return floors != null && !floors.isEmpty();
    }

    /** 주택으로 등록된 층이 있는지. 층별개요가 없으면 표제부 기타용도에 "주택"이 있는지로 대신한다 (상가주택 등). */
    public boolean hasHousingPart() {
        if (hasFloorInfo()) {
            return floors.stream().filter(f -> !f.isRooftop()).anyMatch(FloorUse::isHousing);
        }
        return etcPurpose != null && etcPurpose.contains("주택");
    }

    /** 근린생활시설 층을 "1층(일반음식점·주차장)" 형태로, 낮은 층부터. */
    public List<String> neighborhoodFacilityFloorLabels() {
        return floorLabels(FloorUse::isNeighborhoodFacility);
    }

    /** 주택 층을 "2층(다가구주택)" 형태로, 낮은 층부터. */
    public List<String> housingFloorLabels() {
        return floorLabels(FloorUse::isHousing);
    }

    private List<String> floorLabels(java.util.function.Predicate<FloorUse> filter) {
        if (!hasFloorInfo()) {
            return List.of();
        }
        Map<String, List<String>> purposesByFloor = new LinkedHashMap<>();
        floors.stream()
                .filter(f -> !f.isRooftop())
                .filter(filter)
                .sorted(Comparator.comparingInt((FloorUse f) -> f.underground() ? -f.floorNo() : f.floorNo()))
                .forEach(f -> {
                    List<String> purposes = purposesByFloor.computeIfAbsent(
                            f.floorName() != null ? f.floorName() : "층 정보 없음", k -> new java.util.ArrayList<>());
                    String label = f.purposeLabel();
                    if (label != null && !label.isBlank() && !purposes.contains(label.trim())) {
                        purposes.add(label.trim());
                    }
                });
        return purposesByFloor.entrySet().stream()
                .map(e -> e.getValue().isEmpty() ? e.getKey() : e.getKey() + "(" + String.join("·", e.getValue()) + ")")
                .collect(Collectors.toList());
    }

    static boolean isNeighborhoodFacilityPurpose(String code, String name) {
        if (code != null && code.length() >= 2) {
            return code.startsWith("03") || code.startsWith("04");
        }
        return name != null && name.contains("근린생활시설");
    }

    static boolean isHousingPurpose(String code, String name) {
        if (code != null && code.length() >= 2) {
            return code.startsWith("01") || code.startsWith("02");
        }
        return name != null && name.contains("주택");
    }

    public BuildingInfo withFloors(List<FloorUse> floors) {
        return new BuildingInfo(buildingName, mainPurpose, structureType, useApprovalDate, totalFloorAreaSqm,
                mainPurposeCode, etcPurpose, floors, familyCount);
    }
}

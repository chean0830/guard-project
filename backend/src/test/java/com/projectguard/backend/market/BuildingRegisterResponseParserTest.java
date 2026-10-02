package com.projectguard.backend.market;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingRegisterResponseParserTest {

    private final BuildingRegisterResponseParser parser = new BuildingRegisterResponseParser(new ObjectMapper());

    @Test
    void 표제부의_기타용도까지_읽는다() {
        String body = """
                {"response":{"body":{"items":{"item":{
                  "bldNm":"테스트빌","mainPurpsCdNm":"단독주택","strctCdNm":"철근콘크리트구조",
                  "useAprDay":"20150301","totArea":"412.5","mainPurpsCd":"01000","etcPurps":"다가구주택(8가구)","fmlyCnt":8}}}}}
                """;

        BuildingInfo info = parser.parseFirst(body).orElseThrow();

        assertEquals("단독주택", info.mainPurpose());
        assertEquals("01000", info.mainPurposeCode());
        assertEquals("다가구주택(8가구)", info.etcPurpose());
        assertEquals(412.5, info.totalFloorAreaSqm());
        assertEquals(8, info.familyCount());
    }

    @Test
    void 층별개요에서_근린생활시설_층을_찾는다() {
        String body = """
                {"response":{"body":{"items":{"item":[
                  {"flrGbCd":"20","flrNo":2,"flrNoNm":"2층","mainPurpsCd":"01003","mainPurpsCdNm":"다가구주택","etcPurps":"다가구주택"},
                  {"flrGbCd":"20","flrNo":1,"flrNoNm":"1층","mainPurpsCd":"03005","mainPurpsCdNm":"의원","etcPurps":"의원"},
                  {"flrGbCd":"10","flrNo":1,"flrNoNm":"지1","mainPurpsCd":"04010","mainPurpsCdNm":"학원","etcPurps":""}]}}}}
                """;

        List<BuildingInfo.FloorUse> floors = parser.parseFloors(body);
        BuildingInfo info = new BuildingInfo(null, "단독주택", null, null, null, "01000", null, floors);

        assertEquals(3, floors.size());
        assertTrue(floors.get(2).underground());
        // 지하부터 낮은 층 순서로, 기타용도가 비면 주용도 이름을 쓴다
        assertEquals(List.of("지1(학원)", "1층(의원)"), info.neighborhoodFacilityFloorLabels());
        assertEquals(List.of("2층(다가구주택)"), info.housingFloorLabels());
    }

    @Test
    void 응답이_비거나_깨지면_빈_목록() {
        assertTrue(parser.parseFloors("{\"response\":{\"body\":{\"items\":\"\"}}}").isEmpty());
        assertTrue(parser.parseFloors("not json").isEmpty());
    }
}

package com.projectguard.backend.registry;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실제 등기부등본 샘플(개인정보 포함, 커밋 금지)과 동일한 구조를 가진
 * 가상 데이터(src/test/resources/registry/sample-registry.txt)로 파서를 검증한다.
 */
class RegistryParserTest {

    private final RegistryParser parser = new RegistryParser();

    private String loadSample() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/registry/sample-registry.txt")) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void 주소와_고유번호를_추출한다() throws IOException {
        RegistryAnalysis result = parser.parse(loadSample());

        assertEquals("서울특별시 강남구 테스트로 123 테스트아파트 제101동 제5층 제501호", result.address());
        assertEquals("1234-2020-000001", result.uniqueNumber());
    }

    @Test
    void 소유권보존_항목을_추출한다() throws IOException {
        RegistryAnalysis result = parser.parse(loadSample());

        assertEquals(1, result.ownershipHistory().size());
        OwnershipEntry ownership = result.ownershipHistory().get(0);
        assertEquals(1, ownership.rank());
        assertEquals(OwnershipType.OWNERSHIP_PRESERVATION, ownership.type());
        assertEquals("홍길동", ownership.ownerName());
        assertFalse(ownership.cancelled());
    }

    @Test
    void 가압류는_말소여부까지_반영된다() throws IOException {
        RegistryAnalysis result = parser.parse(loadSample());

        assertEquals(1, result.seizures().size());
        SeizureEntry seizure = result.seizures().get(0);
        assertEquals(2, seizure.rank());
        assertEquals(SeizureType.PROVISIONAL_SEIZURE, seizure.type());
        assertTrue(seizure.cancelled(), "3번 항목이 2번 가압류를 말소했으므로 cancelled=true 여야 한다");
    }

    @Test
    void 근저당권_말소여부와_활성_채권최고액_합계를_계산한다() throws IOException {
        RegistryAnalysis result = parser.parse(loadSample());

        assertEquals(2, result.mortgages().size());

        MortgageEntry first = result.mortgages().get(0);
        assertEquals(1, first.rank());
        assertEquals(300_000_000L, first.maxClaimAmount());
        assertEquals("홍길동", first.debtorName());
        assertEquals("테스트은행", first.mortgageeName());
        assertTrue(first.cancelled(), "2번 항목이 1번 근저당권설정을 말소했으므로 cancelled=true 여야 한다");

        MortgageEntry second = result.mortgages().get(1);
        assertEquals(3, second.rank());
        assertEquals(150_000_000L, second.maxClaimAmount());
        assertEquals("테스트캐피탈", second.mortgageeName());
        assertFalse(second.cancelled());

        assertEquals(150_000_000L, result.totalActiveMortgageAmount(),
                "말소된 1번(3억)은 제외하고 활성 상태인 3번(1.5억)만 합산되어야 한다");
    }

    @Test
    void 등기부등본이_아닌_텍스트는_예외를_던진다() {
        assertThrows(NotRegistryDocumentException.class,
                () -> parser.parse("이것은 영수증입니다.\n합계 12,000원"));
    }

    /**
     * OCR(카메라 촬영)은 표의 우측 컬럼(채무자/근저당권자)을 좌측 컬럼과 다른 블록으로 묶어,
     * 그 값이 엉뚱하게 뒤따르는 말소 항목의 블록에 붙어버리는 경우가 있다 (docs/결정사항.md 15번).
     * 1번 근저당권설정의 채무자/근저당권자가 2번(말소) 항목 뒤에 붙어 나오는 상황을 재현한다.
     */
    @Test
    void 채무자_근저당권자가_뒤_항목에_붙어나와도_순서로_보완한다() {
        String text = String.join("\n",
                "등기사항전부증명서(말소사항 포함)",
                "[집합건물] 테스트 주소",
                "고유번호 0000-0000-000000",
                "【 갑 구 】 (소유권에 관한 사항)",
                "1 소유권보존 2020년1월1일 제1호 소유자 테스트인",
                "【 을 구 】 (소유권 이외의 권리에 관한 사항)",
                "1 근저당권설정 2020년1월1일 제100호 2020년1월1일 설정계약 채권최고액 금100,000,000원",
                "2 1번근저당권설정등기말소 2021년1월1일 제200호 해지",
                "채무자 채무자A",
                "근저당권자 근저당권자B",
                "3 근저당권설정 2022년1월1일 제300호 2022년1월1일 설정계약 채권최고액 금200,000,000원",
                "채무자 채무자C",
                "근저당권자 근저당권자D");

        RegistryAnalysis result = parser.parse(text);

        assertEquals(2, result.mortgages().size());
        MortgageEntry first = result.mortgages().get(0);
        assertEquals(1, first.rank());
        assertEquals("채무자A", first.debtorName(), "블록 분리로 비어있던 채무자를 전체 스캔으로 보완해야 한다");
        assertEquals("근저당권자B", first.mortgageeName());
        assertTrue(first.cancelled());

        MortgageEntry second = result.mortgages().get(1);
        assertEquals(3, second.rank());
        assertEquals("채무자C", second.debtorName(), "이미 올바르게 파싱된 값은 덮어쓰지 않아야 한다");
        assertEquals("근저당권자D", second.mortgageeName());
        assertFalse(second.cancelled());
    }

    @Test
    void 다가구주택_건물_등기부의_주소도_추출한다() throws IOException {
        String buildingRegistry = loadSample()
                .replace("- 집합건물 -", "- 건물 -")
                .replace("[집합건물] 서울특별시 강남구 테스트로 123 테스트아파트 제101동 제5층 제501호",
                        "[건물] 서울특별시 관악구 테스트로 45");

        RegistryAnalysis result = parser.parse(buildingRegistry);

        assertEquals("서울특별시 관악구 테스트로 45", result.address());
        assertEquals(RegistryKind.BUILDING, result.registryKind());
        assertEquals(1, result.ownershipHistory().size());
        assertFalse(result.mortgages().isEmpty());
    }

    @Test
    void 등기부_종류를_구분한다() throws IOException {
        assertEquals(RegistryKind.COLLECTIVE_BUILDING, parser.parse(loadSample()).registryKind());

        String land = loadSample()
                .replace("- 집합건물 -", "- 토지 -")
                .replace("[집합건물]", "[토지]")
                .replace("( 1동의 건물의 표시 )", "( 토지의 표시 )");
        assertEquals(RegistryKind.LAND, parser.parse(land).registryKind());
        assertEquals("서울특별시 강남구 테스트로 123 테스트아파트 제101동 제5층 제501호", parser.parse(land).address());
    }

    @Test
    void 종류_표시가_없으면_UNKNOWN() throws IOException {
        String noMark = loadSample()
                .replace("- 집합건물 -", "")
                .replace("[집합건물]", "");
        assertEquals(RegistryKind.UNKNOWN, parser.parse(noMark).registryKind());
    }
}

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
}

package com.projectguard.backend.lawyer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 변호사가 직접 적는 강점(한 줄 강점·경력·수임료·실적) 저장과 입력 검증. */
@SpringBootTest
@Transactional
class LawyerStrengthsTest {

    @Autowired
    private LawyerAuthService lawyerAuthService;

    private Lawyer lawyer() {
        return lawyerAuthService.signup("strength-" + UUID.randomUUID() + "@example.com", "password123", "김강점", null, "12345",
                List.of(new MockMultipartFile("documents", "license.pdf", "application/pdf", "%PDF-1.4 dummy".getBytes())));
    }

    @Test
    void 강점을_저장하고_빈_값은_비워둔다() {
        Lawyer lawyer = lawyer();

        Lawyer updated = lawyerAuthService.updateStrengths(
                lawyer.getId(), " 전세사기 전문 ", 8, "첫 상담 무료", "  ");

        assertEquals("전세사기 전문", updated.getHeadline());
        assertEquals(8, updated.getCareerYears());
        assertEquals("첫 상담 무료", updated.getFeeInfo());
        assertNull(updated.getAchievements());
    }

    @Test
    void 경력이_범위를_벗어나면_거부한다() {
        Lawyer lawyer = lawyer();

        assertThrows(IllegalArgumentException.class, () -> lawyerAuthService.updateStrengths(lawyer.getId(), null, -1, null, null));
        assertThrows(IllegalArgumentException.class, () -> lawyerAuthService.updateStrengths(lawyer.getId(), null, 71, null, null));
    }

    @Test
    void 글자수_제한을_넘으면_거부한다() {
        Lawyer lawyer = lawyer();

        assertThrows(IllegalArgumentException.class, () ->
                lawyerAuthService.updateStrengths(lawyer.getId(), "가".repeat(101), null, null, null));
    }
}

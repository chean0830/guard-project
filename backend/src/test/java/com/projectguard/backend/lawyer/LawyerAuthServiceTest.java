package com.projectguard.backend.lawyer;

import com.projectguard.backend.auth.EmailAlreadyExistsException;
import com.projectguard.backend.auth.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 변호사 회원가입은 서류 제출 후 PENDING 상태로 저장되며, 관리자가 승인(APPROVED)해야만
 * 로그인이 가능하다는 핵심 흐름을 인메모리 H2 + 실제 JPA 리포지토리로 검증한다.
 */
@SpringBootTest
@Transactional
class LawyerAuthServiceTest {

    @Autowired
    private LawyerAuthService lawyerAuthService;

    @Autowired
    private LawyerRepository lawyerRepository;

    private List<MultipartFile> oneDocument() {
        return List.of(new MockMultipartFile("documents", "bar-license.pdf", "application/pdf", "%PDF-1.4 dummy".getBytes()));
    }

    @Test
    void 회원가입하면_PENDING_상태로_저장되고_로그인은_안된다() {
        Lawyer lawyer = lawyerAuthService.signup(
                "lawyer1@example.com", "password123", "김변호", "법무법인 테스트", "12345", oneDocument());

        assertEquals(LawyerStatus.PENDING, lawyer.getStatus());
        assertThrows(LawyerNotApprovedException.class,
                () -> lawyerAuthService.login("lawyer1@example.com", "password123"));
    }

    @Test
    void 서류를_첨부하지_않으면_가입에_실패한다() {
        assertThrows(IllegalArgumentException.class,
                () -> lawyerAuthService.signup("lawyer2@example.com", "password123", "김변호", null, "12345", List.of()));
    }

    @Test
    void 등록번호가_없으면_가입에_실패한다() {
        assertThrows(IllegalArgumentException.class,
                () -> lawyerAuthService.signup("lawyer3@example.com", "password123", "김변호", null, "", oneDocument()));
    }

    @Test
    void 같은_이메일로_또_신청하면_예외가_난다() {
        lawyerAuthService.signup("dup-lawyer@example.com", "password123", "김변호", null, "12345", oneDocument());
        assertThrows(EmailAlreadyExistsException.class,
                () -> lawyerAuthService.signup("dup-lawyer@example.com", "password456", "이변호", null, "67890", oneDocument()));
    }

    @Test
    void 관리자가_승인하면_로그인할_수_있다() {
        Lawyer lawyer = lawyerAuthService.signup(
                "approved@example.com", "password123", "김변호", null, "12345", oneDocument());
        lawyer.approve();
        lawyerRepository.save(lawyer);

        LawyerAuthResult result = lawyerAuthService.login("approved@example.com", "password123");

        assertTrue(lawyerAuthService.validate(result.token()).isPresent());
        assertEquals("김변호", result.name());
    }

    @Test
    void 관리자가_거절하면_사유와_함께_로그인이_거부된다() {
        Lawyer lawyer = lawyerAuthService.signup(
                "rejected@example.com", "password123", "김변호", null, "12345", oneDocument());
        lawyer.reject("자격증 사본이 확인되지 않습니다.");
        lawyerRepository.save(lawyer);

        LawyerNotApprovedException e = assertThrows(LawyerNotApprovedException.class,
                () -> lawyerAuthService.login("rejected@example.com", "password123"));
        assertTrue(e.getMessage().contains("자격증 사본이 확인되지 않습니다."));
    }

    @Test
    void 비밀번호가_틀리면_승인_여부와_무관하게_로그인에_실패한다() {
        Lawyer lawyer = lawyerAuthService.signup(
                "wrongpw-lawyer@example.com", "password123", "김변호", null, "12345", oneDocument());
        lawyer.approve();
        lawyerRepository.save(lawyer);

        assertThrows(InvalidCredentialsException.class,
                () -> lawyerAuthService.login("wrongpw-lawyer@example.com", "wrongpassword"));
    }

    @Test
    void 로그아웃하면_토큰이_더이상_유효하지_않다() {
        Lawyer lawyer = lawyerAuthService.signup(
                "logout-lawyer@example.com", "password123", "김변호", null, "12345", oneDocument());
        lawyer.approve();
        lawyerRepository.save(lawyer);

        LawyerAuthResult result = lawyerAuthService.login("logout-lawyer@example.com", "password123");
        lawyerAuthService.logout(result.token());

        assertTrue(lawyerAuthService.validate(result.token()).isEmpty());
    }

    @Test
    void 프로필을_수정할_수_있다() {
        Lawyer lawyer = lawyerAuthService.signup(
                "profile-lawyer@example.com", "password123", "김변호", "예전 소속", "12345", oneDocument());

        Lawyer updated = lawyerAuthService.updateProfile(
                lawyer.getId(), "김변호2", "새 소속", "전세사기, 임대차분쟁", "안녕하세요");

        assertEquals("김변호2", updated.getName());
        assertEquals("새 소속", updated.getLawFirm());
        assertEquals("전세사기, 임대차분쟁", updated.getSpecialties());
        assertEquals("안녕하세요", updated.getIntroduction());
    }

    @Test
    void 이메일_알림_설정을_끌_수_있다() {
        Lawyer lawyer = lawyerAuthService.signup(
                "notif-lawyer@example.com", "password123", "김변호", null, "12345", oneDocument());
        assertTrue(lawyer.isEmailNotificationsEnabled());

        lawyerAuthService.updateEmailNotificationsEnabled(lawyer.getId(), false);

        assertFalse(lawyerRepository.findById(lawyer.getId()).get().isEmailNotificationsEnabled());
    }

    @Test
    void 현재_비밀번호가_맞으면_비밀번호를_변경할_수_있다() {
        Lawyer lawyer = lawyerAuthService.signup(
                "changepw-lawyer@example.com", "password123", "김변호", null, "12345", oneDocument());
        lawyer.approve();
        lawyerRepository.save(lawyer);

        lawyerAuthService.changePassword(lawyer.getId(), "password123", "newpassword456");

        assertTrue(lawyerAuthService.login("changepw-lawyer@example.com", "newpassword456").token() != null);
    }

    @Test
    void 현재_비밀번호가_틀리면_비밀번호_변경에_실패한다() {
        Lawyer lawyer = lawyerAuthService.signup(
                "changepwfail-lawyer@example.com", "password123", "김변호", null, "12345", oneDocument());

        assertThrows(InvalidCredentialsException.class,
                () -> lawyerAuthService.changePassword(lawyer.getId(), "wrongpassword", "newpassword456"));
    }
}

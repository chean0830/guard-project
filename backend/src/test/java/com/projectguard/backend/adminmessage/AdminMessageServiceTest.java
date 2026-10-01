package com.projectguard.backend.adminmessage;

import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AdminMessageServiceTest {

    @Autowired
    private AdminMessageService service;

    @Autowired
    private LawyerRepository lawyerRepository;

    private Long newLawyer(String email) {
        return lawyerRepository.save(new Lawyer(email, "hash", "김변호", null, "12345")).getId();
    }

    @Test
    void 보낸_메시지는_안읽음으로_쌓이고_알림함을_열면_모두_읽음이_된다() {
        Long lawyerId = newLawyer("msg-1@example.com");

        service.send(lawyerId, "  프로필 사진을 바꿔주세요  ");
        service.send(lawyerId, "두 번째 안내");

        assertThat(service.unreadCount(lawyerId)).isEqualTo(2);
        assertThat(service.listFor(lawyerId)).extracting(AdminMessage::getContent)
                .containsExactly("두 번째 안내", "프로필 사진을 바꿔주세요");

        service.markAllRead(lawyerId);

        assertThat(service.unreadCount(lawyerId)).isZero();
        assertThat(service.listFor(lawyerId)).allSatisfy(m -> assertThat(m.getReadAt()).isNotNull());
    }

    @Test
    void 다른_변호사의_메시지는_보이지_않는다() {
        Long me = newLawyer("msg-2@example.com");
        Long other = newLawyer("msg-3@example.com");

        service.send(other, "다른 변호사에게");

        assertThat(service.listFor(me)).isEmpty();
        assertThat(service.unreadCount(me)).isZero();
    }

    @Test
    void 빈_메시지나_너무_긴_메시지_없는_변호사는_거절한다() {
        Long lawyerId = newLawyer("msg-4@example.com");

        assertThatThrownBy(() -> service.send(lawyerId, "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.send(lawyerId, "가".repeat(AdminMessage.MAX_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.send(999_999L, "안내")).isInstanceOf(NoSuchElementException.class);
    }
}

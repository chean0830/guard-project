package com.projectguard.backend.adminmessage;

import com.projectguard.backend.lawyer.LawyerRepository;
import com.projectguard.backend.push.PushNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AdminMessageService {

    private final AdminMessageRepository repository;
    private final LawyerRepository lawyerRepository;
    private final PushNotificationService pushNotificationService;

    public AdminMessageService(
            AdminMessageRepository repository,
            LawyerRepository lawyerRepository,
            PushNotificationService pushNotificationService
    ) {
        this.repository = repository;
        this.lawyerRepository = lawyerRepository;
        this.pushNotificationService = pushNotificationService;
    }

    /** 관리자 → 변호사 메시지를 저장하고 변호사 앱에 푸시를 보낸다 (푸시 실패해도 메시지는 저장됨). */
    public AdminMessage send(Long lawyerId, String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("메시지 내용을 입력해주세요.");
        }
        if (text.length() > AdminMessage.MAX_LENGTH) {
            throw new IllegalArgumentException("메시지는 " + AdminMessage.MAX_LENGTH + "자까지 보낼 수 있습니다.");
        }
        if (!lawyerRepository.existsById(lawyerId)) {
            throw new NoSuchElementException("변호사를 찾을 수 없습니다.");
        }
        AdminMessage message = repository.save(new AdminMessage(lawyerId, text));
        pushNotificationService.notifyLawyerAdminMessage(lawyerId, text);
        return message;
    }

    public List<AdminMessage> listFor(Long lawyerId) {
        return repository.findByLawyerIdOrderByCreatedAtDesc(lawyerId);
    }

    public long unreadCount(Long lawyerId) {
        return repository.countByLawyerIdAndReadAtIsNull(lawyerId);
    }

    /** 변호사가 알림함을 열면 안 읽은 메시지를 모두 읽음 처리한다. */
    @Transactional
    public void markAllRead(Long lawyerId) {
        repository.findByLawyerIdAndReadAtIsNull(lawyerId).forEach(AdminMessage::markRead);
    }
}

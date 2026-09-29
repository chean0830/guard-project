package com.projectguard.backend.consultation;

import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerRepository;
import com.projectguard.backend.lawyer.LawyerStatus;
import com.projectguard.backend.push.PushNotificationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 회원-변호사 문의/상담 대화. 회원이 문의를 시작하면 승인된(APPROVED) 변호사 중 한 명과
 * 무작위로 매칭한다 — 기존 데모(10명의 가상 변호사 중 무작위 매칭)와 같은 사용자 경험을
 * 유지하되, 실제 승인된 변호사 계정으로 대체한 것.
 */
@Service
public class ConsultationService {

    private final ConsultationRepository consultationRepository;
    private final ConsultationMessageRepository messageRepository;
    private final LawyerRepository lawyerRepository;
    private final ConsultationMailService mailService;
    private final PushNotificationService pushNotificationService;

    public ConsultationService(
            ConsultationRepository consultationRepository,
            ConsultationMessageRepository messageRepository,
            LawyerRepository lawyerRepository,
            ConsultationMailService mailService,
            PushNotificationService pushNotificationService
    ) {
        this.consultationRepository = consultationRepository;
        this.messageRepository = messageRepository;
        this.lawyerRepository = lawyerRepository;
        this.mailService = mailService;
        this.pushNotificationService = pushNotificationService;
    }

    public Consultation startConsultation(Long userId, String initialMessage) {
        if (initialMessage == null || initialMessage.isBlank()) {
            throw new IllegalArgumentException("문의 내용을 입력해주세요.");
        }
        List<Lawyer> approved = lawyerRepository.findByStatus(LawyerStatus.APPROVED);
        if (approved.isEmpty()) {
            throw new IllegalStateException("현재 상담 가능한 변호사가 없습니다. 잠시 후 다시 시도해주세요.");
        }
        Lawyer lawyer = approved.get(ThreadLocalRandom.current().nextInt(approved.size()));

        Consultation consultation = consultationRepository.save(new Consultation(userId, lawyer.getId()));
        messageRepository.save(new ConsultationMessage(consultation.getId(), SenderType.USER, initialMessage));
        notifyLawyerIfEnabled(lawyer, initialMessage);
        return consultation;
    }

    public ConsultationMessage postMessage(Long consultationId, SenderType senderType, Long senderId, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("메시지 내용을 입력해주세요.");
        }
        Consultation consultation = requireConsultation(consultationId);
        requireParticipant(consultation, senderType, senderId);

        ConsultationMessage message = messageRepository.save(new ConsultationMessage(consultationId, senderType, content));
        consultation.touch();
        consultationRepository.save(consultation);

        if (senderType == SenderType.USER) {
            lawyerRepository.findById(consultation.getLawyerId())
                    .ifPresent(lawyer -> notifyLawyerIfEnabled(lawyer, content));
        } else {
            pushNotificationService.notifyUser(consultation.getUserId(), consultationId, content);
        }
        return message;
    }

    public List<Consultation> listForUser(Long userId) {
        return consultationRepository.findByUserIdOrderByLastMessageAtDesc(userId);
    }

    public List<Consultation> listForLawyer(Long lawyerId) {
        return consultationRepository.findByLawyerIdOrderByLastMessageAtDesc(lawyerId);
    }

    public List<ConsultationMessage> getThreadAsUser(Long consultationId, Long userId) {
        Consultation consultation = requireConsultation(consultationId);
        requireParticipant(consultation, SenderType.USER, userId);
        return readThreadAndMarkOpponentRead(consultationId, SenderType.LAWYER);
    }

    public List<ConsultationMessage> getThreadAsLawyer(Long consultationId, Long lawyerId) {
        Consultation consultation = requireConsultation(consultationId);
        requireParticipant(consultation, SenderType.LAWYER, lawyerId);
        return readThreadAndMarkOpponentRead(consultationId, SenderType.USER);
    }

    public String getLastMessagePreview(Long consultationId) {
        return messageRepository.findTopByConsultationIdOrderByCreatedAtDesc(consultationId)
                .map(ConsultationMessage::getContent)
                .orElse("");
    }

    public long unreadCountForUser(Long consultationId) {
        return messageRepository.countByConsultationIdAndSenderTypeAndReadFalse(consultationId, SenderType.LAWYER);
    }

    public long unreadCountForLawyer(Long consultationId) {
        return messageRepository.countByConsultationIdAndSenderTypeAndReadFalse(consultationId, SenderType.USER);
    }

    public Consultation requireConsultation(Long consultationId) {
        return consultationRepository.findById(consultationId)
                .orElseThrow(() -> new NoSuchElementException("문의를 찾을 수 없습니다."));
    }

    /** 대화방 당사자가 맞는지 확인한다 — 다른 회원/변호사가 남의 문의를 들여다볼 수 없게 막는 핵심 검증. */
    private void requireParticipant(Consultation consultation, SenderType senderType, Long senderId) {
        boolean ok = senderType == SenderType.USER
                ? consultation.getUserId().equals(senderId)
                : consultation.getLawyerId().equals(senderId);
        if (!ok) {
            throw new ConsultationAccessDeniedException("이 문의에 접근할 권한이 없습니다.");
        }
    }

    private List<ConsultationMessage> readThreadAndMarkOpponentRead(Long consultationId, SenderType opponentType) {
        List<ConsultationMessage> messages = messageRepository.findByConsultationIdOrderByCreatedAtAsc(consultationId);
        for (ConsultationMessage message : messages) {
            if (message.getSenderType() == opponentType && !message.isRead()) {
                message.markRead();
                messageRepository.save(message);
            }
        }
        return messages;
    }

    private void notifyLawyerIfEnabled(Lawyer lawyer, String previewText) {
        if (lawyer.isEmailNotificationsEnabled()) {
            mailService.sendNewMessageNotification(lawyer.getEmail(), previewText);
        }
    }
}

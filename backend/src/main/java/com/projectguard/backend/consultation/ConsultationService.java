package com.projectguard.backend.consultation;

import com.projectguard.backend.auth.User;
import com.projectguard.backend.auth.UserRepository;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerRepository;
import com.projectguard.backend.lawyer.LawyerStatus;
import com.projectguard.backend.push.PushNotificationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final UserRepository userRepository;
    private final ConsultationMailService mailService;
    private final PushNotificationService pushNotificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatBlockRepository blockRepository;

    public ConsultationService(
            ConsultationRepository consultationRepository,
            ConsultationMessageRepository messageRepository,
            LawyerRepository lawyerRepository,
            UserRepository userRepository,
            ConsultationMailService mailService,
            PushNotificationService pushNotificationService,
            ApplicationEventPublisher eventPublisher,
            ChatBlockRepository blockRepository
    ) {
        this.consultationRepository = consultationRepository;
        this.messageRepository = messageRepository;
        this.lawyerRepository = lawyerRepository;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.pushNotificationService = pushNotificationService;
        this.eventPublisher = eventPublisher;
        this.blockRepository = blockRepository;
    }

    public Consultation startConsultation(Long userId, String initialMessage) {
        requireMessage(initialMessage);
        List<Lawyer> approved = listAvailableLawyersFor(userId);
        if (approved.isEmpty()) {
            throw new IllegalStateException("현재 상담 가능한 변호사가 없습니다. 잠시 후 다시 시도해주세요.");
        }
        Lawyer lawyer = approved.get(ThreadLocalRandom.current().nextInt(approved.size()));
        return openConsultation(userId, lawyer, initialMessage);
    }

    /** 회원이 직접 고른 변호사와 상담을 시작한다(유료 이용권 사용 — payment.PaymentService에서 호출). */
    public Consultation startConsultationWith(Long userId, Long lawyerId, String initialMessage) {
        requireMessage(initialMessage);
        Lawyer lawyer = lawyerRepository.findById(lawyerId)
                .filter(l -> l.getStatus() == LawyerStatus.APPROVED && !l.isBlocked())
                .filter(l -> blockRepository.findByUserIdAndLawyerId(userId, l.getId()).isEmpty())
                .orElseThrow(() -> new IllegalStateException("선택한 변호사와 지금은 상담할 수 없습니다. 다른 변호사를 선택해주세요."));
        return openConsultation(userId, lawyer, initialMessage);
    }

    /** 상담 가능한(승인됐고 정지되지 않은) 변호사 목록. */
    public List<Lawyer> listAvailableLawyers() {
        return lawyerRepository.findByStatus(LawyerStatus.APPROVED).stream()
                .filter(l -> !l.isBlocked())
                .toList();
    }

    /** 이 회원이 새로 상담할 수 있는 변호사 — 서로 차단 관계(어느 쪽이 걸었든)가 있는 변호사는 뺀다. */
    public List<Lawyer> listAvailableLawyersFor(Long userId) {
        Set<Long> blockedLawyerIds = blockRepository.findByUserId(userId).stream()
                .map(ChatBlock::getLawyerId)
                .collect(Collectors.toSet());
        return listAvailableLawyers().stream()
                .filter(l -> !blockedLawyerIds.contains(l.getId()))
                .toList();
    }

    /** viewerType 입장에서 본 차단 상태: NONE / BLOCKED_BY_ME(내가 차단) / BLOCKED_ME(상대가 나를 차단). */
    public String blockStateFor(Consultation consultation, SenderType viewerType) {
        List<ChatBlock> blocks = blockRepository.findByUserIdAndLawyerId(consultation.getUserId(), consultation.getLawyerId());
        if (blocks.stream().anyMatch(b -> b.getBlockerType() == viewerType)) {
            return "BLOCKED_BY_ME";
        }
        return blocks.isEmpty() ? "NONE" : "BLOCKED_ME";
    }

    private void requireMessage(String initialMessage) {
        if (initialMessage == null || initialMessage.isBlank()) {
            throw new IllegalArgumentException("문의 내용을 입력해주세요.");
        }
    }

    private Consultation openConsultation(Long userId, Lawyer lawyer, String initialMessage) {
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
        if (isCounterpartBlocked(consultation, senderType)) {
            throw new IllegalStateException("상대방이 이용 정지되어 더 이상 메시지를 보낼 수 없습니다.");
        }
        switch (blockStateFor(consultation, senderType)) {
            case "BLOCKED_BY_ME" -> throw new IllegalStateException("차단한 상대에게는 메시지를 보낼 수 없어요. 설정에서 차단을 해제할 수 있어요.");
            case "BLOCKED_ME" -> throw new IllegalStateException("상대방에게 메시지를 보낼 수 없어요.");
            default -> { }
        }

        ConsultationMessage message = messageRepository.save(new ConsultationMessage(consultationId, senderType, content));
        consultation.touch();
        consultationRepository.save(consultation);
        eventPublisher.publishEvent(new ConsultationMessagePostedEvent(
                consultationId, senderType, content, message.getCreatedAt()));

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

    /** 대화방 당사자인지 검증한 뒤 대화방을 돌려준다. 신고 접수 등 다른 기능에서 같은 검증을 재사용한다. */
    public Consultation requireConsultationFor(Long consultationId, SenderType participantType, Long participantId) {
        Consultation consultation = requireConsultation(consultationId);
        requireParticipant(consultation, participantType, participantId);
        return consultation;
    }

    /** viewerType 입장에서 본 상대방(회원이면 변호사, 변호사면 회원)이 이용 정지 상태인지. */
    public boolean isCounterpartBlocked(Consultation consultation, SenderType viewerType) {
        return viewerType == SenderType.USER
                ? lawyerRepository.findById(consultation.getLawyerId()).map(Lawyer::isBlocked).orElse(false)
                : userRepository.findById(consultation.getUserId()).map(User::isBlocked).orElse(false);
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

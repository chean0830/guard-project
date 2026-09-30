package com.projectguard.backend.consultation;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/** 대화방에서 상대방을 차단하고, 각자 설정 화면에서 차단 목록을 보고 해제한다. */
@Service
public class ChatBlockService {

    private final ChatBlockRepository blockRepository;
    private final ConsultationService consultationService;

    public ChatBlockService(ChatBlockRepository blockRepository, ConsultationService consultationService) {
        this.blockRepository = blockRepository;
        this.consultationService = consultationService;
    }

    /** 대화방 당사자만 그 대화방의 상대방을 차단할 수 있다. 이미 차단했으면 그대로 둔다. */
    public ChatBlock blockCounterpart(Long consultationId, SenderType blockerType, Long blockerId) {
        Consultation consultation = consultationService.requireConsultationFor(consultationId, blockerType, blockerId);
        return blockRepository
                .findByBlockerTypeAndUserIdAndLawyerId(blockerType, consultation.getUserId(), consultation.getLawyerId())
                .orElseGet(() -> blockRepository.save(
                        new ChatBlock(blockerType, consultation.getUserId(), consultation.getLawyerId())));
    }

    /** 내가 차단한 목록. 회원이면 차단한 변호사들, 변호사면 차단한 회원들. */
    public List<ChatBlock> listMine(SenderType blockerType, Long blockerId) {
        return blockerType == SenderType.USER
                ? blockRepository.findByBlockerTypeAndUserIdOrderByCreatedAtDesc(SenderType.USER, blockerId)
                : blockRepository.findByBlockerTypeAndLawyerIdOrderByCreatedAtDesc(SenderType.LAWYER, blockerId);
    }

    /** 내가 건 차단만 해제할 수 있다 — 상대방이 나를 차단한 것은 내가 풀 수 없다. */
    public void unblock(Long blockId, SenderType blockerType, Long blockerId) {
        ChatBlock block = blockRepository.findById(blockId)
                .filter(b -> b.getBlockerType() == blockerType)
                .filter(b -> blockerId.equals(blockerType == SenderType.USER ? b.getUserId() : b.getLawyerId()))
                .orElseThrow(() -> new NoSuchElementException("차단 내역을 찾을 수 없습니다."));
        blockRepository.delete(block);
    }
}

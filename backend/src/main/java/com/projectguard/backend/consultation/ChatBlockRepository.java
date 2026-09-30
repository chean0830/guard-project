package com.projectguard.backend.consultation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatBlockRepository extends JpaRepository<ChatBlock, Long> {
    List<ChatBlock> findByUserIdAndLawyerId(Long userId, Long lawyerId);

    Optional<ChatBlock> findByBlockerTypeAndUserIdAndLawyerId(SenderType blockerType, Long userId, Long lawyerId);

    /** 회원이 차단한 변호사 목록(blockerType=USER) 또는 회원을 차단한 변호사까지 포함한 전체 관계 조회에 쓴다. */
    List<ChatBlock> findByUserId(Long userId);

    List<ChatBlock> findByBlockerTypeAndUserIdOrderByCreatedAtDesc(SenderType blockerType, Long userId);

    List<ChatBlock> findByBlockerTypeAndLawyerIdOrderByCreatedAtDesc(SenderType blockerType, Long lawyerId);
}

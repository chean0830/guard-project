package com.projectguard.backend.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByOrderId(String orderId);

    long countByUserIdAndStatusAndConsultationIdIsNull(Long userId, PaymentStatus status);

    /** 이용권 사용 시 같은 이용권이 두 번 쓰이지 않도록 잠그고 가져온다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PaymentOrder> findByUserIdAndStatusAndConsultationIdIsNullOrderByPaidAtAsc(Long userId, PaymentStatus status);
}

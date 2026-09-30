package com.projectguard.backend.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByOrderId(String orderId);

    /** 취소·환불 처리 중 같은 주문이 동시에 사용되거나 두 번 취소되지 않도록 잠그고 가져온다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.orderId = :orderId")
    Optional<PaymentOrder> findByOrderIdForUpdate(String orderId);

    /** 결제 내역 화면: 결제 창만 열었다 닫은 주문(READY)은 빼고 보여준다. */
    List<PaymentOrder> findByUserIdAndStatusNotOrderByCreatedAtDesc(Long userId, PaymentStatus excluded);

    List<PaymentOrder> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    List<PaymentOrder> findByStatusNotOrderByCreatedAtDesc(PaymentStatus excluded);

    long countByUserIdAndStatusAndConsultationIdIsNull(Long userId, PaymentStatus status);

    /** 이용권 사용 시 같은 이용권이 두 번 쓰이지 않도록 잠그고 가져온다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PaymentOrder> findByUserIdAndStatusAndConsultationIdIsNullOrderByPaidAtAsc(Long userId, PaymentStatus status);
}

package com.projectguard.backend.common;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.Optional;

public interface RateLimitCounterRepository extends JpaRepository<RateLimitCounter, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RateLimitCounter> findByCounterKeyAndDay(String counterKey, LocalDate day);
}

package com.projectguard.backend.lawyer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LawyerRepository extends JpaRepository<Lawyer, Long> {
    Optional<Lawyer> findByEmail(String email);

    List<Lawyer> findByStatus(LawyerStatus status);
}

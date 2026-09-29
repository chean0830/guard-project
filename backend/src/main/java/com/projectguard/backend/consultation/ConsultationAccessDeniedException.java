package com.projectguard.backend.consultation;

public class ConsultationAccessDeniedException extends RuntimeException {
    public ConsultationAccessDeniedException(String message) {
        super(message);
    }
}

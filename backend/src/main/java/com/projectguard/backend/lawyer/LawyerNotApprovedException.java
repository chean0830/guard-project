package com.projectguard.backend.lawyer;

public class LawyerNotApprovedException extends RuntimeException {
    public LawyerNotApprovedException(String message) {
        super(message);
    }
}

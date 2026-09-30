package com.projectguard.backend.payment;

public class PaymentException extends RuntimeException {
    public PaymentException(String message) {
        super(message);
    }
}

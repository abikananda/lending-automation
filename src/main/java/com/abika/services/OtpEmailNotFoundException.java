package com.abika.services;

/**
 * Exception thrown when OTP email is not found
 */
public class OtpEmailNotFoundException extends CustomException {
    public OtpEmailNotFoundException(String message) {
        super(message);
    }

    public OtpEmailNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}


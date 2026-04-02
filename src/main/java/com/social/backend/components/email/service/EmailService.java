package com.social.backend.components.email.service;

public interface EmailService {
    void sendVerificationEmail(String toEmail, String username, String verificationToken);

    void sendPasswordResetEmail(String toEmail, String username, String resetToken);
}
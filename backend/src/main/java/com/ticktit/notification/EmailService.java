package com.ticktit.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends emails. Other modules call these methods and never use SMTP themselves.
 * Sending runs in the background (@Async) so the API answers without waiting 1-3 s for the mail server.
 * A failed send is logged, not thrown: the user can always press "resend".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Async
    public void sendVerificationLink(String to, String name, String link) {
        send(to, "Verify your email for Ticktit", """
                Hi %s,

                Please confirm your email address by opening this link:

                %s

                The link works once and expires in 24 hours.
                If you did not sign up for Ticktit, you can ignore this email.
                """.formatted(name, link));
    }

    @Async
    public void sendLoginOtp(String to, String name, String otp) {
        send(to, "Your Ticktit sign-in code: " + otp, """
                Hi %s,

                Your sign-in code is: %s

                It expires in 5 minutes. Never share this code with anyone.
                If you did not try to sign in, change your password.
                """.formatted(name, otp));
    }

    @Async
    public void sendPasswordResetOtp(String to, String name, String otp) {
        send(to, "Reset your Ticktit password", """
                Hi %s,

                Your password reset code is: %s

                It expires in 5 minutes. Never share this code with anyone.
                If you did not ask to reset your password, you can ignore this email.
                """.formatted(name, otp));
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
            log.info("Email sent: {}", subject.replaceAll("\\d{6}", "******"));   // never log codes or addresses
        } catch (MailException e) {
            log.error("Email sending failed", e);
        }
    }
}

package com.ticktit.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Sends real emails to Mailpit (docker compose up -d mailpit) and reads them back. Skipped if Mailpit is down. */
@SpringBootTest
class EmailServiceTest {

    private static final String MAILPIT = "http://localhost:8025/api/v1";
    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired EmailService emailService;

    @BeforeEach
    void mailpitMustBeRunning() {
        assumeTrue(call("DELETE", "/messages") != null, "Mailpit is not running on localhost:8025");
    }

    @Test
    void verificationLinkArrives() throws Exception {
        emailService.sendVerificationLink("asha@example.com", "Asha", "http://localhost:5173/verify-email?token=abc123");

        String inbox = waitForMessages();
        assertTrue(inbox.contains("Verify your email for Ticktit"));
        assertTrue(inbox.contains("asha@example.com"));
    }

    @Test
    void loginOtpArrivesWithTheCodeInTheSubject() throws Exception {
        emailService.sendLoginOtp("asha@example.com", "Asha", "482913");

        assertTrue(waitForMessages().contains("Your Ticktit sign-in code: 482913"));
    }

    /** Sending is @Async, so poll until Mailpit has it (max ~5 s). */
    private String waitForMessages() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            String body = call("GET", "/messages");
            if (body != null && !body.contains("\"total\":0")) {
                return body;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No email arrived in Mailpit");
    }

    private String call(String method, String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(MAILPIT + path))
                    .method(method, HttpRequest.BodyPublishers.noBody()).build();
            return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            return null;
        }
    }
}

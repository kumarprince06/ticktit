package com.ticktit.auth.service;

import com.ticktit.auth.entity.User;
import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuthCleanupJobTest {

    @Autowired AuthCleanupJob job;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM users WHERE email LIKE 'cleanup-%@example.com'");
    }

    @Test
    void deletesOldUnverifiedSignUpsOnly() {
        save("cleanup-old-pending@example.com", UserStatus.PENDING_VERIFICATION);
        save("cleanup-new-pending@example.com", UserStatus.PENDING_VERIFICATION);
        save("cleanup-old-active@example.com", UserStatus.ACTIVE);
        jdbc.update("UPDATE users SET created_at = now() - interval '8 days' WHERE email LIKE 'cleanup-old-%'");

        job.run();

        assertTrue(userRepository.findByEmail("cleanup-old-pending@example.com").isEmpty());
        assertTrue(userRepository.findByEmail("cleanup-new-pending@example.com").isPresent());   // still has time
        assertTrue(userRepository.findByEmail("cleanup-old-active@example.com").isPresent());    // real account
    }

    private void save(String email, UserStatus status) {
        User user = new User();
        user.setName("Cleanup");
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setStatus(status);
        if (status == UserStatus.ACTIVE) {
            user.setEmailVerifiedAt(Instant.now());
        }
        userRepository.save(user);
    }
}

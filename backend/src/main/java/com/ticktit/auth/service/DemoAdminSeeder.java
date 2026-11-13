package com.ticktit.auth.service;

import com.ticktit.auth.entity.User;
import com.ticktit.auth.entity.UserStatus;
import com.ticktit.auth.repository.UserRepository;
import com.ticktit.common.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

/**
 * Creates a ready-to-use ADMIN account for local development (already verified, so it can sign in at once).
 * Same switch as the demo catalogue: off in prod (application-prod.yaml). Never overwrites an existing account.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoAdminSeeder {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.demo-data.admin-email}")
    private String email;

    @Value("${app.demo-data.admin-password}")
    private String password;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(normalized).isPresent()) {
            return;
        }
        User admin = new User();
        admin.setName("Ticktit Admin");
        admin.setEmail(normalized);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setEmailVerifiedAt(Instant.now());
        userRepository.save(admin);
        log.info("Demo data: created admin account {}", normalized);
    }
}

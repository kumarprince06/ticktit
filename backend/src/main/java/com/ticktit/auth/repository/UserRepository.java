package com.ticktit.auth.repository;

import com.ticktit.auth.entity.User;
import com.ticktit.auth.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);           // Signup check, login, password reset, email verification
    Optional<User> findByGoogleSub(String googleSub);   // Google login
    Optional<User> findByPublicId(UUID publicId);       // Turn JWT "sub" into a user
    boolean existsByEmail(String email);                // Optional: quick signup check

    @Modifying
    int deleteByStatusAndCreatedAtBefore(UserStatus status, Instant cutoff);   // daily job: never-verified sign-ups

}

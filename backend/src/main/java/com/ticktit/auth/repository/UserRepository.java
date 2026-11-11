package com.ticktit.auth.repository;

import com.ticktit.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);           // Signup check, login, password reset, email verification
    Optional<User> findByGoogleSub(String googleSub);   // Google login
    Optional<User> findByPublicId(UUID publicId);       // Turn JWT "sub" into a user
    boolean existsByEmail(String email);                // Optional: quick signup check

}

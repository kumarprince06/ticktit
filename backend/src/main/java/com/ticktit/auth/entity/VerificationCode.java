package com.ticktit.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "verification_codes")
@Getter
@Setter
@NoArgsConstructor
public class VerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String codeHash;          // SHA-256 hex of OTP or email-link token, never raw

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CodePurpose purpose;            // values come from the CodePurpose enum, no DB check

    @Column(nullable = false)
    private int attempts = 0;         // reject after 5 wrong tries

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;                // NULL = not used yet

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}

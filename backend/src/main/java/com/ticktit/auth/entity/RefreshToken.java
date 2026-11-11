package com.ticktit.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private UUID familyId;

    @Column(nullable = false, unique = true)
    private String tokenHash;          // SHA-256 hex of the refresh token, never raw

    @Column(nullable = false, updatable = false) 
    private Instant familyExpiresAt;            // Sign-in + 7 days, never extended; all tokens in the same family expire together
    private Instant usedAt;         // set once when rotated; reuse => revoke family,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}

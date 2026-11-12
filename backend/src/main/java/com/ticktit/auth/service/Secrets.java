package com.ticktit.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Random tokens and hashing for refresh tokens, email links and OTPs. */
public final class Secrets {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Secrets() {
    }

    /** 32 random bytes (256 bits), URL-safe, for refresh tokens and email links. */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 6-digit one-time code, e.g. "048213". */
    public static String randomOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** Compares two hashes in constant time, so response timing reveals nothing about how close a guess was. */
    public static boolean hashesEqual(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 as 64 hex chars. Only this is stored, never the raw value. */
    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}

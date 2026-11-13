package com.ticktit.common.idempotency;

import com.ticktit.common.enums.ErrorCode;
import com.ticktit.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Makes a money-changing call safe to repeat (ADR-010). The client sends one Idempotency-Key per user action;
 * double-clicks and network retries reuse it.
 * <ul>
 *   <li>First time: the key is claimed, the work runs, and its response is stored, all in the caller's transaction.
 *       If the work fails, everything rolls back and the key is free again, so retrying works.</li>
 *   <li>Same key, same request: the stored response comes back and the work does not run again.</li>
 *   <li>Same key, different request: 422 IDEMPOTENCY_KEY_REUSED.</li>
 *   <li>Same key at the same moment: Postgres makes the second insert wait for the first transaction, then the
 *       second request gets the first one's response. No duplicates, no "try again later".</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    /** Must run inside the caller's transaction: the key, the work and the stored response commit together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T run(long userId, String key, String endpoint, Object request, Class<T> responseType, Supplier<T> work) {
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String requestHash = sha256(json.writeValueAsString(request));

        int claimed = jdbc.update("""
                INSERT INTO idempotency_keys (user_id, idem_key, endpoint, request_hash, response_status, response_body, expires_at)
                VALUES (?, ?, ?, ?, 0, '{}'::jsonb, now() + interval '24 hours')
                ON CONFLICT (user_id, idem_key) DO NOTHING""", userId, key, endpoint, requestHash);

        if (claimed == 0) {   // seen before: replay it, but only for the very same request
            record Stored(String endpoint, String hash, String body) {
            }
            List<Stored> stored = jdbc.query(
                    "SELECT endpoint, request_hash, response_body::text FROM idempotency_keys WHERE user_id = ? AND idem_key = ?",
                    (rs, i) -> new Stored(rs.getString(1), rs.getString(2), rs.getString(3)), userId, key);
            if (stored.isEmpty() || !stored.getFirst().endpoint().equals(endpoint)
                    || !stored.getFirst().hash().equals(requestHash)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            String body = stored.getFirst().body();
            return "null".equals(body) ? null : json.readValue(body, responseType);
        }

        T response = work.get();
        jdbc.update("UPDATE idempotency_keys SET response_status = 200, response_body = ?::jsonb WHERE user_id = ? AND idem_key = ?",
                json.writeValueAsString(response), userId, key);
        return response;
    }

    /** Keys only matter for retries within a day. */
    @Scheduled(cron = "0 45 3 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void deleteExpired() {
        jdbc.update("DELETE FROM idempotency_keys WHERE expires_at < now()");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

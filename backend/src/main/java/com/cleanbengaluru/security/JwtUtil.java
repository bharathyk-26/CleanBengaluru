package com.cleanbengaluru.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Creates and validates JSON Web Tokens.
 *
 * A JWT is three base64 parts: header.payload.signature.
 * We sign with HMAC-SHA256 using a server-side secret, so a client can read the
 * payload but cannot change it — any edit breaks the signature.
 */
@Component
@Slf4j
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // HS256 requires a key of at least 256 bits = 32 ASCII characters.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Subject = email (our unique login id). We also embed role and userId as custom claims. */
    public String generateToken(String email, String role, Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object v = parseClaims(token).get("userId");
        return v == null ? null : Long.valueOf(v.toString());
    }

    public String extractRole(String token) {
        Object v = parseClaims(token).get("role");
        return v == null ? null : v.toString();
    }

    /** Returns false for a tampered, malformed or expired token — never throws to the caller. */
    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

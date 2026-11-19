package com.porros365.evmining.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public class JwtTokenProvider {

    private static final String DEFAULT_SECRET = "Porros365EvPlusClvMiningDistributedPlatformSecretKey2026_HMAC_256!";
    private static final long EXPIRATION_SECONDS = 3600L * 8L; // 8 hours

    private final SecretKey secretKey;

    public JwtTokenProvider() {
        this(System.getenv().getOrDefault("JWT_SECRET", DEFAULT_SECRET));
    }

    public JwtTokenProvider(String rawSecret) {
        String effective = (rawSecret != null && rawSecret.length() >= 32) ? rawSecret : DEFAULT_SECRET;
        this.secretKey = Keys.hmacShaKeyFor(effective.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(EXPIRATION_SECONDS);
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public Claims validateAndParseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationSeconds() {
        return EXPIRATION_SECONDS;
    }
}

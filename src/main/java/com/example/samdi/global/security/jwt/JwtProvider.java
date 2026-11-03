package com.example.samdi.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpirationMinutes;
    private final long refreshTokenExpirationMinutes;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-minutes}") long accessTokenExpirationMinutes,
            @Value("${jwt.refresh-token-expiration-minutes}") long refreshTokenExpirationMinutes
    ) {
        if (accessTokenExpirationMinutes <= 0 || refreshTokenExpirationMinutes <= 0) {
            throw new IllegalArgumentException("JWT 만료 시간은 0보다 커야 합니다.");
        }
        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
        this.refreshTokenExpirationMinutes = refreshTokenExpirationMinutes;

    }

    private String createToken(String id, long expirationMinutes, String type, String sessionId) {
        Instant now = Instant.now();
        Instant end = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(id)
                .issuedAt(Date.from(now))
                .expiration(Date.from(end))
                .signWith(secretKey)
                .claim("type",type)
                .claim("sessionId",sessionId)
                .compact();
    }

    private Claims validateToken(String token, String expectedType) {
        Claims parseJwt = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!expectedType.equals(parseJwt.get("type",String.class))) {
            throw new JwtException("올바르지 않은 토큰 종류입니다.");
        }
        if (parseJwt.getSubject() == null || parseJwt.getSubject().isBlank() || parseJwt.getExpiration() == null) {
            throw new JwtException("유저 아이디를 찾지 못했습니다.");
        }

        try {
            Long id = Long.parseLong(parseJwt.getSubject());
            if (id <= 0) {
                throw new JwtException("올바르지 않은 사용자 ID입니다.");
            }
        } catch (NumberFormatException e) {
            throw new JwtException("사용자 ID가 올바른 숫자 형식이 아닙니다.",e);
        }

        return parseJwt;
    }

    public record TokenIdentity(String userId, String sessionId) {}

    private TokenIdentity identity(String token, String type) {
        Claims claims = validateToken(token, type);
        String sessionId = claims.get("sessionId", String.class);
        if (sessionId == null || sessionId.isBlank()) {
            throw new JwtException("세션 정보를 찾지 못했습니다.");
        }
        return new TokenIdentity(claims.getSubject(), sessionId);
    }

    public TokenIdentity getAccessIdentity(String token) {
        return identity(token, "access");
    }

    public TokenIdentity getRefreshIdentity(String token) {
        return identity(token, "refresh");
    }

    public String createAccessToken(String id, String sessionId) {
       return createToken(id,accessTokenExpirationMinutes,"access", sessionId);
    }

    public String createRefreshToken(String id, String sessionId) {
        return createToken(id,refreshTokenExpirationMinutes,"refresh",sessionId);
    }

    public String getUserId(String token) {
        return validateToken(token,"access").getSubject();
    }

    public String getUserIdFromRefreshToken(String token) {
        return validateToken(token,"refresh").getSubject();
    }

    public String getSessionId(String token) {
        return validateToken(token,"access").get("sessionId",String.class);
    }
}

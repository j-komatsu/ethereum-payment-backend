package com.web3pay.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirySeconds;

    public JwtService(
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.expiry-seconds:86400}") long expirySeconds) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirySeconds = expirySeconds;
    }

    public String generate(String address) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(address)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirySeconds)))
                .signWith(secretKey)
                .compact();
    }

    public String extractAddress(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}

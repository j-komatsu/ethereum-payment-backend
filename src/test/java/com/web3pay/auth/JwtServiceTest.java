package com.web3pay.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtZm9yLXVuaXQtdGVzdHMtb25seSE=";
    private static final String ADDRESS = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 86400L);
    }

    @Test
    void generate_returnsNonBlankToken() {
        String token = jwtService.generate(ADDRESS);
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
    }

    @Test
    void extractAddress_validToken_returnsOriginalAddress() {
        String token = jwtService.generate(ADDRESS);
        assertThat(jwtService.extractAddress(token)).isEqualTo(ADDRESS);
    }

    @Test
    void generate_differentAddresses_produceDifferentTokens() {
        String token1 = jwtService.generate(ADDRESS);
        String token2 = jwtService.generate("0x" + "b".repeat(40));
        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void extractAddress_expiredToken_throwsJwtException() {
        // expirySeconds=-1 → expiration is 1 second in the past
        JwtService shortLived = new JwtService(SECRET, -1L);
        String expiredToken = shortLived.generate(ADDRESS);

        assertThatThrownBy(() -> jwtService.extractAddress(expiredToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractAddress_tamperedSignature_throwsJwtException() {
        String token = jwtService.generate(ADDRESS);
        // Replace the signature part with garbage
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "tampered_signature";

        assertThatThrownBy(() -> jwtService.extractAddress(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractAddress_randomString_throwsJwtException() {
        assertThatThrownBy(() -> jwtService.extractAddress("not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }
}

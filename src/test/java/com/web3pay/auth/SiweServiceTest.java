package com.web3pay.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SiweServiceTest {

    private static final String TEST_JWT_SECRET = "dGVzdC1zZWNyZXQtZm9yLXVuaXQtdGVzdHMtb25seSE=";

    @Mock
    SiweNonceRepository nonceRepository;

    SiweService siweService;
    JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_JWT_SECRET, 86400L);
        siweService = new SiweService(nonceRepository, jwtService, "localhost", 137L, 300L);
    }

    @Test
    void generateNonce_savesAndReturnsNonce() {
        when(nonceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String nonce = siweService.generateNonce();

        assertThat(nonce).hasSize(32);
        verify(nonceRepository).save(argThat(n -> n.getNonce().equals(nonce) && !n.isUsed()));
    }

    @Test
    void verify_validSignature_returnsJwt() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        String address = "0x" + Keys.getAddress(keyPair);
        String nonce = "a".repeat(32);
        String message = buildSiweMessage(address, nonce);

        SiweNonce nonceEntity = SiweNonce.builder()
                .nonce(nonce)
                .expiresAt(Instant.now().plusSeconds(300))
                .used(false)
                .build();
        when(nonceRepository.findById(nonce)).thenReturn(Optional.of(nonceEntity));
        when(nonceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String hexSig = signMessage(message, keyPair);
        String jwt = siweService.verify(message, hexSig, address);

        assertThat(jwt).isNotBlank();
        assertThat(jwtService.extractAddress(jwt)).isEqualTo(address.toLowerCase());
        assertThat(nonceEntity.isUsed()).isTrue();
    }

    @Test
    void verify_expiredNonce_throwsSiweException() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        String address = "0x" + Keys.getAddress(keyPair);
        String nonce = "b".repeat(32);
        String message = buildSiweMessage(address, nonce);

        SiweNonce nonceEntity = SiweNonce.builder()
                .nonce(nonce)
                .expiresAt(Instant.now().minusSeconds(1))
                .used(false)
                .build();
        when(nonceRepository.findById(nonce)).thenReturn(Optional.of(nonceEntity));

        String hexSig = signMessage(message, keyPair);
        assertThatThrownBy(() -> siweService.verify(message, hexSig, address))
                .isInstanceOf(SiweException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verify_usedNonce_throwsSiweException() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        String address = "0x" + Keys.getAddress(keyPair);
        String nonce = "c".repeat(32);
        String message = buildSiweMessage(address, nonce);

        SiweNonce nonceEntity = SiweNonce.builder()
                .nonce(nonce)
                .expiresAt(Instant.now().plusSeconds(300))
                .used(true)
                .build();
        when(nonceRepository.findById(nonce)).thenReturn(Optional.of(nonceEntity));

        String hexSig = signMessage(message, keyPair);
        assertThatThrownBy(() -> siweService.verify(message, hexSig, address))
                .isInstanceOf(SiweException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void verify_wrongSigner_throwsSiweException() throws Exception {
        ECKeyPair claimedKeyPair = Keys.createEcKeyPair();
        ECKeyPair signerKeyPair = Keys.createEcKeyPair();
        String claimedAddress = "0x" + Keys.getAddress(claimedKeyPair);
        String nonce = "d".repeat(32);
        String message = buildSiweMessage(claimedAddress, nonce);

        SiweNonce nonceEntity = SiweNonce.builder()
                .nonce(nonce)
                .expiresAt(Instant.now().plusSeconds(300))
                .used(false)
                .build();
        when(nonceRepository.findById(nonce)).thenReturn(Optional.of(nonceEntity));

        // Sign with a different key pair
        String hexSig = signMessage(message, signerKeyPair);
        assertThatThrownBy(() -> siweService.verify(message, hexSig, claimedAddress))
                .isInstanceOf(SiweException.class)
                .hasMessageContaining("Signature verification failed");
    }

    @Test
    void verify_invalidAddressFormat_throwsSiweException() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        String nonce = "e".repeat(32);
        String message = buildSiweMessage("0x1234", nonce);

        assertThatThrownBy(() -> siweService.verify(message, "0x" + "0".repeat(130), "0x1234"))
                .isInstanceOf(SiweException.class)
                .hasMessageContaining("Invalid address format");
    }

    @Test
    void verify_unknownNonce_throwsSiweException() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        String address = "0x" + Keys.getAddress(keyPair);
        String nonce = "f".repeat(32);
        String message = buildSiweMessage(address, nonce);

        when(nonceRepository.findById(nonce)).thenReturn(Optional.empty());

        String hexSig = signMessage(message, keyPair);
        assertThatThrownBy(() -> siweService.verify(message, hexSig, address))
                .isInstanceOf(SiweException.class)
                .hasMessageContaining("Invalid nonce");
    }

    // ---- helpers ----

    private String buildSiweMessage(String address, String nonce) {
        return "localhost wants you to sign in with your Ethereum account:\n" +
               address + "\n\n" +
               "Sign in to Web3Pay\n\n" +
               "URI: http://localhost/api/v1/auth\n" +
               "Version: 1\n" +
               "Chain ID: 137\n" +
               "Nonce: " + nonce + "\n" +
               "Issued At: 2026-05-27T00:00:00Z";
    }

    private String signMessage(String message, ECKeyPair keyPair) {
        Sign.SignatureData sigData = Sign.signPrefixedMessage(
                message.getBytes(StandardCharsets.UTF_8), keyPair);
        byte[] combined = new byte[65];
        System.arraycopy(sigData.getR(), 0, combined, 0, 32);
        System.arraycopy(sigData.getS(), 0, combined, 32, 32);
        combined[64] = sigData.getV()[0];
        return Numeric.toHexString(combined);
    }
}

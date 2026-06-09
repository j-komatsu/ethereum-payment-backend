package com.web3pay.auth;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SiweService {

    private static final Pattern ETH_ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");
    private static final int MAX_MESSAGE_LENGTH = 4096;

    private final SiweNonceRepository nonceRepository;
    private final JwtService jwtService;
    private final String domain;
    private final long chainId;
    private final long nonceTtlSeconds;

    public SiweService(
            SiweNonceRepository nonceRepository,
            JwtService jwtService,
            @Value("${auth.siwe.domain:localhost}") String domain,
            @Value("${auth.siwe.chain-id:137}") long chainId,
            @Value("${auth.siwe.nonce-ttl-seconds:300}") long nonceTtlSeconds) {
        this.nonceRepository = nonceRepository;
        this.jwtService = jwtService;
        this.domain = domain;
        this.chainId = chainId;
        this.nonceTtlSeconds = nonceTtlSeconds;
    }

    @Transactional
    public String generateNonce() {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        nonceRepository.save(SiweNonce.builder()
                .nonce(nonce)
                .expiresAt(Instant.now().plusSeconds(nonceTtlSeconds))
                .used(false)
                .build());
        return nonce;
    }

    @Transactional
    public String verify(String message, String hexSignature, String address) {
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new SiweException("SIWE message too long");
        }

        // Normalize line endings before parsing and signature verification
        String normalizedMessage = message.replace("\r\n", "\n").replace("\r", "\n");

        if (!ETH_ADDRESS_PATTERN.matcher(address).matches()) {
            throw new SiweException("Invalid address format");
        }

        String nonce = extractField(normalizedMessage, "Nonce: ");
        String msgChainId = extractField(normalizedMessage, "Chain ID: ");
        String msgDomain = extractDomain(normalizedMessage);
        String msgAddress = extractMessageAddress(normalizedMessage);
        String version = extractField(normalizedMessage, "Version: ");

        if (!this.domain.equals(msgDomain)) {
            throw new SiweException("Domain mismatch");
        }
        if (!String.valueOf(chainId).equals(msgChainId)) {
            throw new SiweException("Chain ID mismatch");
        }
        if (!"1".equals(version)) {
            throw new SiweException("Unsupported SIWE version: " + version);
        }
        // Verify that the address in the message body matches the claimed address
        if (!msgAddress.equalsIgnoreCase(address)) {
            throw new SiweException("Address in message does not match request address");
        }

        SiweNonce nonceEntity = nonceRepository.findById(nonce)
                .orElseThrow(() -> new SiweException("Invalid nonce"));
        if (nonceEntity.isUsed()) {
            throw new SiweException("Nonce already used");
        }
        if (nonceEntity.getExpiresAt().isBefore(Instant.now())) {
            throw new SiweException("Nonce expired");
        }

        // recoveredAddress is the ground truth — use it for the JWT subject
        String recoveredAddress = recoverAddress(normalizedMessage, hexSignature);
        if (!recoveredAddress.equalsIgnoreCase(address)) {
            throw new SiweException("Signature verification failed");
        }

        nonceEntity.setUsed(true);
        nonceRepository.save(nonceEntity);

        return jwtService.generate(recoveredAddress.toLowerCase());
    }

    private String extractField(String message, String prefix) {
        String found = null;
        for (String line : message.split("\n")) {
            if (line.startsWith(prefix)) {
                if (found != null) {
                    throw new SiweException("Duplicate field '" + prefix.trim() + "' in SIWE message");
                }
                found = line.substring(prefix.length()).trim();
            }
        }
        if (found == null) {
            throw new SiweException("Field '" + prefix.trim() + "' not found in SIWE message");
        }
        return found;
    }

    private String extractDomain(String message) {
        String[] lines = message.split("\n");
        if (lines.length == 0) {
            throw new SiweException("Invalid SIWE message: empty");
        }
        String firstLine = lines[0];
        String suffix = " wants you to sign in with your Ethereum account:";
        if (!firstLine.endsWith(suffix)) {
            throw new SiweException("Invalid SIWE message format");
        }
        return firstLine.substring(0, firstLine.length() - suffix.length());
    }

    private String extractMessageAddress(String message) {
        String[] lines = message.split("\n");
        if (lines.length < 2) {
            throw new SiweException("Invalid SIWE message: too short");
        }
        String addr = lines[1].trim();
        if (!ETH_ADDRESS_PATTERN.matcher(addr).matches()) {
            throw new SiweException("Invalid address in SIWE message body");
        }
        return addr;
    }

    private String recoverAddress(String message, String hexSignature) {
        try {
            byte[] sigBytes = Numeric.hexStringToByteArray(hexSignature);
            if (sigBytes.length != 65) {
                throw new SiweException("Signature must be 65 bytes (130 hex chars)");
            }
            byte[] r = Arrays.copyOfRange(sigBytes, 0, 32);
            byte[] s = Arrays.copyOfRange(sigBytes, 32, 64);
            // Read as unsigned to avoid signed-byte misinterpretation
            int vInt = sigBytes[64] & 0xFF;
            if (vInt < 27) vInt += 27;
            if (vInt != 27 && vInt != 28) {
                throw new SiweException("Invalid signature v value: " + vInt);
            }

            Sign.SignatureData sigData = new Sign.SignatureData((byte) vInt, r, s);
            BigInteger pubKey = Sign.signedPrefixedMessageToKey(
                    message.getBytes(StandardCharsets.UTF_8), sigData);
            return "0x" + Keys.getAddress(pubKey);
        } catch (SiweException e) {
            throw e;
        } catch (Exception e) {
            throw new SiweException("Failed to recover address from signature", e);
        }
    }
}

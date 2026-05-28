package com.web3pay.auth;

import com.web3pay.auth.dto.AuthResponse;
import com.web3pay.auth.dto.NonceResponse;
import com.web3pay.auth.dto.VerifyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "SIWE (Sign-In With Ethereum) authentication")
public class SiweController {

    private final SiweService siweService;

    @PostMapping("/nonce")
    @Operation(summary = "Get a one-time nonce for SIWE message construction")
    public NonceResponse nonce() {
        return new NonceResponse(siweService.generateNonce());
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify a signed SIWE message and issue a JWT")
    public AuthResponse verify(@Valid @RequestBody VerifyRequest request) {
        String token = siweService.verify(request.message(), request.signature(), request.address());
        return new AuthResponse(token);
    }
}

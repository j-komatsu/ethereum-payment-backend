package com.web3pay.chain.permit;

import com.web3pay.chain.ChainRegistry;
import com.web3pay.payment.PaymentOrderRepository;
import com.web3pay.token.StablecoinType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class PermitServiceTest {

    @Mock
    ChainRegistry chainRegistry;

    @Mock
    PaymentOrderRepository orderRepository;

    PermitService permitService;

    @BeforeEach
    void setUp() throws Exception {
        ECKeyPair spenderKeyPair = Keys.createEcKeyPair();
        String spenderPrivateKey = Numeric.toHexStringNoPrefix(spenderKeyPair.getPrivateKey());
        permitService = new PermitService(chainRegistry, orderRepository, spenderPrivateKey);
    }

    @Test
    void computeDomainSeparator_isDeterministic() {
        byte[] first = permitService.computeDomainSeparator(StablecoinType.JPYC);
        byte[] second = permitService.computeDomainSeparator(StablecoinType.JPYC);
        assertThat(first).hasSize(32).isEqualTo(second);
    }

    @Test
    void computeDomainSeparator_differsPerToken() {
        byte[] jpyc = permitService.computeDomainSeparator(StablecoinType.JPYC);
        byte[] usdc = permitService.computeDomainSeparator(StablecoinType.USDC);
        assertThat(jpyc).isNotEqualTo(usdc);
    }

    /**
     * Core EIP-712 security invariant: sign the digest with a known key,
     * then verify that Sign.signedMessageHashToKey recovers the same address.
     * This matches exactly what verifyPermitSignature() does in execute().
     */
    @Test
    void buildDigest_roundTrip_signatureVerifies() throws Exception {
        ECKeyPair ownerKeyPair = Keys.createEcKeyPair();
        String ownerAddress = "0x" + Keys.getAddress(ownerKeyPair);
        String spenderAddress = "0x" + Keys.getAddress(Keys.createEcKeyPair());

        byte[] domainSeparator = permitService.computeDomainSeparator(StablecoinType.JPYC);
        BigInteger value = BigInteger.valueOf(1_000_000L);
        BigInteger nonce = BigInteger.ZERO;
        BigInteger deadline = BigInteger.valueOf(Instant.now().plusSeconds(1800).getEpochSecond());

        byte[] digest = permitService.buildDigest(
                domainSeparator, ownerAddress, spenderAddress, value, nonce, deadline);

        assertThat(digest).hasSize(32);

        // Sign raw digest (no additional hashing — EIP-712 digest is already keccak256)
        Sign.SignatureData sigData = Sign.signMessage(digest, ownerKeyPair, false);
        BigInteger recoveredKey = Sign.signedMessageHashToKey(digest, sigData);
        String recovered = "0x" + Keys.getAddress(recoveredKey);

        assertThat(recovered).isEqualToIgnoringCase(ownerAddress);
    }

    @Test
    void buildDigest_differentValues_produceDifferentDigests() {
        byte[] ds = permitService.computeDomainSeparator(StablecoinType.JPYC);
        String owner = "0x" + "a".repeat(40);
        String spender = "0x" + "b".repeat(40);
        BigInteger deadline = BigInteger.valueOf(9999999999L);

        byte[] digest1 = permitService.buildDigest(ds, owner, spender,
                BigInteger.valueOf(1_000_000L), BigInteger.ZERO, deadline);
        byte[] digest2 = permitService.buildDigest(ds, owner, spender,
                BigInteger.valueOf(2_000_000L), BigInteger.ZERO, deadline);

        assertThat(digest1).isNotEqualTo(digest2);
    }

    @Test
    void buildTypedData_noSpenderConfigured_throwsPermitException() {
        PermitService noSpender = new PermitService(chainRegistry, orderRepository, "");
        assertThatThrownBy(() -> noSpender.buildTypedData("order1", "0x" + "1".repeat(40)))
                .isInstanceOf(PermitException.class)
                .hasMessageContaining("Spender wallet not configured");
    }

    @Test
    void buildTypedData_invalidOwnerAddress_throwsPermitException() {
        assertThatThrownBy(() -> permitService.buildTypedData("order1", "notAnAddress"))
                .isInstanceOf(PermitException.class)
                .hasMessageContaining("Invalid Ethereum address");
    }

    @Test
    void execute_noSpenderConfigured_throwsPermitException() {
        PermitService noSpender = new PermitService(chainRegistry, orderRepository, "");
        long deadline = Instant.now().plusSeconds(1800).getEpochSecond();
        assertThatThrownBy(() -> noSpender.execute("order1", "0x" + "1".repeat(40),
                                                    deadline, "0", "0x" + "0".repeat(130)))
                .isInstanceOf(PermitException.class)
                .hasMessageContaining("Spender wallet not configured");
    }

    @Test
    void execute_invalidOwnerAddress_throwsPermitException() {
        long deadline = Instant.now().plusSeconds(1800).getEpochSecond();
        assertThatThrownBy(() -> permitService.execute("order1", "notAnAddress",
                                                       deadline, "0", "0x" + "0".repeat(130)))
                .isInstanceOf(PermitException.class)
                .hasMessageContaining("Invalid Ethereum address");
    }

    @Test
    void execute_pastDeadline_throwsPermitException() {
        long pastDeadline = Instant.now().minusSeconds(60).getEpochSecond();
        assertThatThrownBy(() -> permitService.execute("order1", "0x" + "1".repeat(40),
                                                       pastDeadline, "0", "0x" + "0".repeat(130)))
                .isInstanceOf(PermitException.class)
                .hasMessageContaining("Deadline has already passed");
    }
}

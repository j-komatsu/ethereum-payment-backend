package com.web3pay.chain.permit;

import com.web3pay.chain.ChainCommunicationException;
import com.web3pay.chain.ChainRegistry;
import com.web3pay.payment.PaymentOrder;
import com.web3pay.payment.PaymentOrderNotFoundException;
import com.web3pay.payment.PaymentOrderRepository;
import com.web3pay.payment.PaymentStatus;
import com.web3pay.token.StablecoinType;
import com.web3pay.util.TokenAmountConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PermitService {

    private static final Pattern ETH_ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");
    private static final Pattern TX_HASH_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{64}$");

    private static final BigInteger PERMIT_GAS_LIMIT = BigInteger.valueOf(80_000);
    private static final BigInteger TRANSFER_FROM_GAS_LIMIT = BigInteger.valueOf(120_000);
    private static final int PERMIT_DEADLINE_SECONDS = 1800; // 30 minutes

    private static final byte[] EIP712_DOMAIN_TYPEHASH = Hash.sha3(
            "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"
                    .getBytes(StandardCharsets.UTF_8));

    private static final byte[] PERMIT_TYPEHASH = Hash.sha3(
            "Permit(address owner,address spender,uint256 value,uint256 nonce,uint256 deadline)"
                    .getBytes(StandardCharsets.UTF_8));

    private static final byte[] EIP712_PREFIX = {0x19, 0x01};

    private final ChainRegistry chainRegistry;
    private final PaymentOrderRepository orderRepository;
    private final Credentials spenderCredentials;
    private final String spenderAddress;

    public PermitService(
            ChainRegistry chainRegistry,
            PaymentOrderRepository orderRepository,
            @Value("${permit.spender-private-key:}") String spenderPrivateKey) {
        this.chainRegistry = chainRegistry;
        this.orderRepository = orderRepository;
        if (spenderPrivateKey != null && !spenderPrivateKey.isBlank()) {
            this.spenderCredentials = Credentials.create(spenderPrivateKey);
            this.spenderAddress = spenderCredentials.getAddress();
            log.info("Spender wallet configured: {}", spenderAddress);
        } else {
            this.spenderCredentials = null;
            this.spenderAddress = null;
            log.warn("Spender wallet not configured — permit execution will be unavailable");
        }
    }

    /**
     * Builds EIP-712 typed data for the frontend to sign via eth_signTypedData_v4.
     * Fetches the current permit nonce from the contract.
     */
    public PermitTypedData buildTypedData(String paymentOrderId, String ownerAddress) {
        validateAddress(ownerAddress);
        if (spenderAddress == null) {
            throw new PermitException("Spender wallet not configured");
        }

        PaymentOrder order = getValidatedOrder(paymentOrderId);
        StablecoinType token = order.getToken();
        validatePermitSupport(token);

        Web3j web3j = chainRegistry.resolve(token.getChainId());

        BigInteger nonce = fetchNonce(web3j, token.getContractAddress(), ownerAddress);
        BigInteger value = TokenAmountConverter.toRaw(order.getExpectedAmount(), token.getDecimals());
        long deadline = Instant.now().plusSeconds(PERMIT_DEADLINE_SECONDS).getEpochSecond();

        return new PermitTypedData(
                "Permit",
                new PermitTypedData.Domain(
                        token.getPermitName(),
                        token.getPermitVersion(),
                        token.getChainId(),
                        token.getContractAddress()),
                Map.of(
                        "EIP712Domain", PermitTypedData.EIP712_DOMAIN_FIELDS,
                        "Permit", PermitTypedData.PERMIT_FIELDS),
                new PermitTypedData.Message(
                        ownerAddress.toLowerCase(),
                        spenderAddress.toLowerCase(),
                        value.toString(),
                        nonce.toString(),
                        String.valueOf(deadline)));
    }

    /**
     * Executes permit() then transferFrom() on-chain, then confirms the PaymentOrder.
     * No @Transactional — DB operations are short-lived individual transactions; RPC I/O runs outside
     * any DB transaction to avoid holding connections for up to 240 seconds.
     */
    public PermitTxResponse execute(String paymentOrderId, String ownerAddress,
                                    long deadline, String clientNonceStr, String hexSignature) {
        validateAddress(ownerAddress);
        if (spenderCredentials == null) {
            throw new PermitException("Spender wallet not configured");
        }
        if (deadline <= Instant.now().getEpochSecond()) {
            throw new PermitException("Deadline has already passed");
        }

        PaymentOrder order = getValidatedOrder(paymentOrderId);
        StablecoinType token = order.getToken();
        validatePermitSupport(token);

        Web3j web3j = chainRegistry.resolve(token.getChainId());
        BigInteger value = TokenAmountConverter.toRaw(order.getExpectedAmount(), token.getDecimals());

        // Validate nonce: client must send the nonce used for signing, must match current on-chain nonce
        BigInteger clientNonce = parseNonce(clientNonceStr);
        BigInteger currentNonce = fetchNonce(web3j, token.getContractAddress(), ownerAddress);
        if (!clientNonce.equals(currentNonce)) {
            throw new PermitException("Nonce mismatch — please re-fetch typed data and re-sign");
        }

        // Parse and validate signature
        byte[] sigBytes = Numeric.hexStringToByteArray(hexSignature);
        if (sigBytes.length != 65) {
            throw new PermitException("Signature must be 65 bytes");
        }
        byte[] r = Arrays.copyOfRange(sigBytes, 0, 32);
        byte[] s = Arrays.copyOfRange(sigBytes, 32, 64);
        int vInt = sigBytes[64] & 0xFF;
        if (vInt < 27) vInt += 27;
        if (vInt != 27 && vInt != 28) {
            throw new PermitException("Invalid signature v value: " + vInt);
        }

        // Verify signature locally before spending gas
        verifyPermitSignature(token, ownerAddress, spenderAddress, value,
                currentNonce, BigInteger.valueOf(deadline), (byte) vInt, r, s);

        // Atomically claim order: PENDING → PROCESSING (prevents double-execution)
        int claimed = orderRepository.updateStatusConditionally(
                paymentOrderId, PaymentStatus.PENDING, PaymentStatus.PROCESSING);
        if (claimed == 0) {
            throw new PermitException("Order is not available for permit execution");
        }

        // Submit permit() transaction (outside DB transaction — may take up to 120s)
        String permitTxHash = sendPermit(web3j, token, ownerAddress, value,
                BigInteger.valueOf(deadline), (byte) vInt, r, s);
        log.info("Permit TX submitted: {}", permitTxHash);
        waitForReceipt(web3j, permitTxHash, "permit");

        // Submit transferFrom() transaction
        String transferTxHash = sendTransferFrom(web3j, token, ownerAddress,
                order.getReceiverAddress(), value);
        log.info("TransferFrom TX submitted: {}", transferTxHash);
        TransactionReceipt transferReceipt = waitForReceipt(web3j, transferTxHash, "transferFrom");

        if (!transferReceipt.isStatusOK()) {
            throw new PermitException("transferFrom reverted: txHash=" + transferTxHash);
        }

        // Validate txHash format before persisting (CLAUDE.md security rule)
        if (!TX_HASH_PATTERN.matcher(transferTxHash).matches()) {
            throw new PermitException("Unexpected transferFrom txHash format");
        }

        // Confirm order (own short DB transaction via save())
        order.setStatus(PaymentStatus.CONFIRMED);
        order.setTxHash(transferTxHash);
        order.setSenderAddress(ownerAddress.toLowerCase());
        order.setConfirmedAt(Instant.now());
        orderRepository.save(order);

        log.info("Payment confirmed via Permit: orderId={} txHash={}", paymentOrderId, transferTxHash);
        return new PermitTxResponse(paymentOrderId, permitTxHash, transferTxHash, "CONFIRMED");
    }

    // ---- EIP-712 helpers (package-private for unit testing) ----

    byte[] computeDomainSeparator(StablecoinType token) {
        byte[] nameHash = Hash.sha3(token.getPermitName().getBytes(StandardCharsets.UTF_8));
        byte[] versionHash = Hash.sha3(token.getPermitVersion().getBytes(StandardCharsets.UTF_8));

        byte[] encoded = new byte[5 * 32];
        System.arraycopy(EIP712_DOMAIN_TYPEHASH, 0, encoded, 0, 32);
        System.arraycopy(nameHash, 0, encoded, 32, 32);
        System.arraycopy(versionHash, 0, encoded, 64, 32);
        System.arraycopy(Numeric.toBytesPadded(BigInteger.valueOf(token.getChainId()), 32), 0, encoded, 96, 32);
        byte[] addrBytes = Numeric.hexStringToByteArray(Numeric.cleanHexPrefix(token.getContractAddress()));
        System.arraycopy(addrBytes, 0, encoded, 128 + 12, 20);
        return Hash.sha3(encoded);
    }

    byte[] buildDigest(byte[] domainSeparator, String owner, String spender,
                       BigInteger value, BigInteger nonce, BigInteger deadline) {
        // abi.encode(PERMIT_TYPEHASH, owner, spender, value, nonce, deadline)
        byte[] encoded = new byte[6 * 32];
        System.arraycopy(PERMIT_TYPEHASH, 0, encoded, 0, 32);
        System.arraycopy(Numeric.hexStringToByteArray(Numeric.cleanHexPrefix(owner)), 0, encoded, 32 + 12, 20);
        System.arraycopy(Numeric.hexStringToByteArray(Numeric.cleanHexPrefix(spender)), 0, encoded, 64 + 12, 20);
        System.arraycopy(Numeric.toBytesPadded(value, 32), 0, encoded, 96, 32);
        System.arraycopy(Numeric.toBytesPadded(nonce, 32), 0, encoded, 128, 32);
        System.arraycopy(Numeric.toBytesPadded(deadline, 32), 0, encoded, 160, 32);

        byte[] structHash = Hash.sha3(encoded);

        // keccak256("\x19\x01" || domainSeparator || structHash)
        byte[] digestInput = new byte[66];
        digestInput[0] = EIP712_PREFIX[0];
        digestInput[1] = EIP712_PREFIX[1];
        System.arraycopy(domainSeparator, 0, digestInput, 2, 32);
        System.arraycopy(structHash, 0, digestInput, 34, 32);
        return Hash.sha3(digestInput);
    }

    // ---- Private helpers ----

    private void verifyPermitSignature(StablecoinType token, String owner, String spender,
                                        BigInteger value, BigInteger nonce, BigInteger deadline,
                                        byte v, byte[] r, byte[] s) {
        try {
            byte[] domainSeparator = computeDomainSeparator(token);
            byte[] digest = buildDigest(domainSeparator, owner, spender, value, nonce, deadline);
            Sign.SignatureData sigData = new Sign.SignatureData(v, r, s);
            BigInteger recoveredKey = Sign.signedMessageHashToKey(digest, sigData);
            String recovered = "0x" + Keys.getAddress(recoveredKey);
            if (!recovered.equalsIgnoreCase(owner)) {
                throw new PermitException("Permit signature verification failed");
            }
        } catch (PermitException e) {
            throw e;
        } catch (Exception e) {
            throw new PermitException("Failed to verify permit signature", e);
        }
    }

    private BigInteger fetchNonce(Web3j web3j, String contractAddress, String ownerAddress) {
        Function f = new Function("nonces",
                List.of(new Address(ownerAddress)),
                List.of(new TypeReference<Uint256>() {}));
        return (BigInteger) callView(web3j, contractAddress, f);
    }

    private Object callView(Web3j web3j, String contractAddress, Function function) {
        try {
            String encodedCall = FunctionEncoder.encode(function);
            EthCall result = web3j.ethCall(
                    Transaction.createEthCallTransaction(null, contractAddress, encodedCall),
                    DefaultBlockParameterName.LATEST).send();
            if (result.hasError()) {
                throw new ChainCommunicationException("eth_call error: " + result.getError().getMessage());
            }
            List<Type> decoded = FunctionReturnDecoder.decode(
                    result.getValue(), function.getOutputParameters());
            if (decoded.isEmpty()) {
                throw new ChainCommunicationException("eth_call returned empty result for " + function.getName());
            }
            return decoded.get(0).getValue();
        } catch (ChainCommunicationException e) {
            throw e;
        } catch (IOException e) {
            throw new ChainCommunicationException("eth_call IO error: " + e.getMessage(), e);
        }
    }

    private String sendPermit(Web3j web3j, StablecoinType token, String owner,
                               BigInteger value, BigInteger deadline, byte v, byte[] r, byte[] s) {
        Function f = new Function("permit",
                List.of(
                        new Address(owner),
                        new Address(spenderAddress),
                        new Uint256(value),
                        new Uint256(deadline),
                        new Uint8(BigInteger.valueOf(v & 0xFF)),
                        new Bytes32(r),
                        new Bytes32(s)),
                List.of());
        return sendTransaction(web3j, token, FunctionEncoder.encode(f), PERMIT_GAS_LIMIT);
    }

    private String sendTransferFrom(Web3j web3j, StablecoinType token,
                                     String from, String to, BigInteger amount) {
        Function f = new Function("transferFrom",
                List.of(new Address(from), new Address(to), new Uint256(amount)),
                List.of(new TypeReference<Bool>() {}));
        return sendTransaction(web3j, token, FunctionEncoder.encode(f), TRANSFER_FROM_GAS_LIMIT);
    }

    private String sendTransaction(Web3j web3j, StablecoinType token, String data, BigInteger gasLimit) {
        try {
            RawTransactionManager txManager = new RawTransactionManager(
                    web3j, spenderCredentials, token.getChainId());

            // Use EIP-1559 (supported by both Ethereum Mainnet and Polygon since their respective upgrades)
            BigInteger maxPriorityFeePerGas = web3j.ethMaxPriorityFeePerGas().send().getMaxPriorityFeePerGas();
            BigInteger baseFee = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false)
                    .send().getBlock().getBaseFeePerGas();
            BigInteger maxFeePerGas = baseFee.multiply(BigInteger.TWO).add(maxPriorityFeePerGas);

            EthSendTransaction sent = txManager.sendEIP1559Transaction(
                    (long) token.getChainId(),
                    maxPriorityFeePerGas,
                    maxFeePerGas,
                    gasLimit,
                    token.getContractAddress(),
                    data,
                    BigInteger.ZERO);
            if (sent.hasError()) {
                throw new ChainCommunicationException("TX error: " + sent.getError().getMessage());
            }
            return sent.getTransactionHash();
        } catch (ChainCommunicationException e) {
            throw e;
        } catch (IOException e) {
            throw new ChainCommunicationException("TX send IO error: " + e.getMessage(), e);
        }
    }

    private TransactionReceipt waitForReceipt(Web3j web3j, String txHash, String label) {
        try {
            TransactionReceiptProcessor processor = new PollingTransactionReceiptProcessor(
                    web3j, 3_000, 40); // poll every 3s, up to 120s
            TransactionReceipt receipt = processor.waitForTransactionReceipt(txHash);
            if (!receipt.isStatusOK()) {
                throw new PermitException(label + " TX reverted: " + txHash);
            }
            return receipt;
        } catch (PermitException e) {
            throw e;
        } catch (Exception e) {
            throw new ChainCommunicationException("Error waiting for " + label + " receipt", e);
        }
    }

    private PaymentOrder getValidatedOrder(String paymentOrderId) {
        PaymentOrder order = orderRepository.findById(paymentOrderId)
                .orElseThrow(() -> new PaymentOrderNotFoundException(paymentOrderId));
        if (order.getStatus() != PaymentStatus.PENDING) {
            throw new PermitException("Order " + paymentOrderId + " is not PENDING");
        }
        if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(Instant.now())) {
            throw new PermitException("Order " + paymentOrderId + " has expired");
        }
        return order;
    }

    private void validatePermitSupport(StablecoinType token) {
        if (!token.isPermitSupported()) {
            throw new PermitException(token.name() + " does not support EIP-2612 permit");
        }
    }

    private BigInteger parseNonce(String nonceStr) {
        try {
            return new BigInteger(nonceStr);
        } catch (NumberFormatException e) {
            throw new PermitException("Invalid nonce format: " + nonceStr);
        }
    }

    private void validateAddress(String address) {
        if (!ETH_ADDRESS_PATTERN.matcher(address).matches()) {
            throw new PermitException("Invalid Ethereum address: " + address);
        }
    }
}

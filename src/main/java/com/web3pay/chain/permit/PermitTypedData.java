package com.web3pay.chain.permit;

import java.util.List;
import java.util.Map;

/**
 * EIP-712 typed data structure returned to the frontend for wallet signing
 * via eth_signTypedData_v4.
 */
public record PermitTypedData(
        String primaryType,
        Domain domain,
        Map<String, List<TypeField>> types,
        Message message
) {
    public record Domain(
            String name,
            String version,
            long chainId,
            String verifyingContract
    ) {}

    public record TypeField(String name, String type) {}

    public record Message(
            String owner,
            String spender,
            String value,
            String nonce,
            String deadline
    ) {}

    public static final List<TypeField> EIP712_DOMAIN_FIELDS = List.of(
            new TypeField("name", "string"),
            new TypeField("version", "string"),
            new TypeField("chainId", "uint256"),
            new TypeField("verifyingContract", "address")
    );

    public static final List<TypeField> PERMIT_FIELDS = List.of(
            new TypeField("owner", "address"),
            new TypeField("spender", "address"),
            new TypeField("value", "uint256"),
            new TypeField("nonce", "uint256"),
            new TypeField("deadline", "uint256")
    );
}

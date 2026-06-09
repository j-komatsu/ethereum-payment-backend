package com.web3pay.chain.permit;

public class PermitException extends RuntimeException {

    public PermitException(String message) {
        super(message);
    }

    public PermitException(String message, Throwable cause) {
        super(message, cause);
    }
}

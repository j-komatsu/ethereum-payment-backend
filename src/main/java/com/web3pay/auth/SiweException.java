package com.web3pay.auth;

public class SiweException extends RuntimeException {

    public SiweException(String message) {
        super(message);
    }

    public SiweException(String message, Throwable cause) {
        super(message, cause);
    }
}

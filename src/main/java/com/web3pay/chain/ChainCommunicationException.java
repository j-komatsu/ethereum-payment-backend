package com.web3pay.chain;

public class ChainCommunicationException extends RuntimeException {

    public ChainCommunicationException(String message) {
        super(message);
    }

    public ChainCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}

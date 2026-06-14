package com.web3pay.streaming;

public class StreamNotFoundException extends RuntimeException {

    public StreamNotFoundException(Long streamId) {
        super("Sablier stream not found: streamId=" + streamId);
    }
}

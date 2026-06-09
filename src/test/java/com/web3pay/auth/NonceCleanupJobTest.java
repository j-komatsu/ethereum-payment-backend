package com.web3pay.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NonceCleanupJobTest {

    @Mock
    SiweNonceRepository nonceRepository;

    @InjectMocks
    NonceCleanupJob cleanupJob;

    @Test
    void deleteExpiredNonces_callsRepositoryWithCurrentTime() {
        Instant before = Instant.now();
        cleanupJob.deleteExpiredNonces();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(nonceRepository).deleteExpiredBefore(captor.capture());

        Instant passedInstant = captor.getValue();
        assertThat(passedInstant).isBetween(before, after);
    }
}

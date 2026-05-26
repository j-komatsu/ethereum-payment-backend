package com.web3pay.chain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetBalance;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChainServiceTest {

    @Mock
    private Web3j web3j;

    @InjectMocks
    private ChainService chainService;

    private static final String ADDRESS = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";

    @SuppressWarnings("unchecked")
    private void mockWeb3jBalance(String hexWei) throws IOException {
        EthGetBalance ethGetBalance = new EthGetBalance();
        ethGetBalance.setResult(hexWei);
        Request<?, EthGetBalance> request = mock(Request.class);
        when(request.send()).thenReturn(ethGetBalance);
        when(web3j.ethGetBalance(anyString(), any())).thenReturn((Request) request);
    }

    @Test
    void getEthBalance_returnsCorrectEthAndWei() throws IOException {
        // 1 ETH = 10^18 wei = 0xde0b6b3a7640000
        mockWeb3jBalance("0xde0b6b3a7640000");

        EthBalanceResponse response = chainService.getEthBalance(ADDRESS);

        assertThat(new BigDecimal(response.balanceEth())).isEqualByComparingTo("1");
        assertThat(response.balanceWei()).isEqualTo(BigInteger.TEN.pow(18).toString());
    }

    @Test
    void getEthBalance_zeroBalance_returnsZero() throws IOException {
        mockWeb3jBalance("0x0");

        EthBalanceResponse response = chainService.getEthBalance(ADDRESS);

        assertThat(response.balanceEth()).isEqualTo("0");
        assertThat(response.balanceWei()).isEqualTo("0");
    }

    @Test
    void getEthBalance_normalizesLowercaseToChecksumAddress() throws IOException {
        mockWeb3jBalance("0x0");
        String lowercase = ADDRESS.toLowerCase();

        EthBalanceResponse response = chainService.getEthBalance(lowercase);

        assertThat(response.address()).isNotEqualTo(lowercase);
        assertThat(response.address()).isEqualTo(ADDRESS);
    }

    @Test
    void getEthBalance_whenIOException_throwsChainCommunicationException() throws IOException {
        @SuppressWarnings("unchecked")
        Request<?, EthGetBalance> request = mock(Request.class);
        when(request.send()).thenThrow(new IOException("connection refused"));
        when(web3j.ethGetBalance(anyString(), any())).thenReturn((Request) request);

        assertThatThrownBy(() -> chainService.getEthBalance(ADDRESS))
                .isInstanceOf(ChainCommunicationException.class);
    }
}

package com.web3pay.chain;

import com.web3pay.auth.JwtService;
import com.web3pay.config.SecurityConfig;
import com.web3pay.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChainController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class ChainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChainService chainService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_ADDRESS = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";

    @Test
    void getEthBalance_validAddress_returns200WithBody() throws Exception {
        EthBalanceResponse response = new EthBalanceResponse(VALID_ADDRESS, "1", "1000000000000000000");
        when(chainService.getEthBalance(anyString())).thenReturn(response);

        mockMvc.perform(get("/api/v1/chain/eth-balance/" + VALID_ADDRESS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value(VALID_ADDRESS))
                .andExpect(jsonPath("$.balanceEth").value("1"))
                .andExpect(jsonPath("$.balanceWei").value("1000000000000000000"));
    }

    @Test
    void getEthBalance_invalidAddressFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/chain/eth-balance/not-an-address"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEthBalance_addressWithoutHexPrefix_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/chain/eth-balance/d8dA6BF26964aF9D7eEd9e03E53415D37aA96045"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEthBalance_chainCommunicationError_returns502() throws Exception {
        when(chainService.getEthBalance(anyString()))
                .thenThrow(new ChainCommunicationException("node down", new IOException()));

        mockMvc.perform(get("/api/v1/chain/eth-balance/" + VALID_ADDRESS))
                .andExpect(status().isBadGateway());
    }
}

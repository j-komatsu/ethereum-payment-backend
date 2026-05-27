package com.web3pay.chain;

import com.web3pay.auth.JwtService;
import com.web3pay.config.SecurityConfig;
import com.web3pay.exception.GlobalExceptionHandler;
import com.web3pay.token.StablecoinType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TokenBalanceController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class TokenBalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenBalanceService tokenBalanceService;

    @MockBean
    private JwtService jwtService;

    private static final String VALID_ADDRESS = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";

    @Test
    void getTokenBalance_validRequest_returns200() throws Exception {
        TokenBalanceResponse response = new TokenBalanceResponse(VALID_ADDRESS, "JPYC", "1000", "1000000000000000000000");
        when(tokenBalanceService.getTokenBalance(any(), eq(StablecoinType.JPYC))).thenReturn(response);

        mockMvc.perform(get("/api/v1/chain/token-balance")
                        .param("address", VALID_ADDRESS)
                        .param("token", "JPYC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value(VALID_ADDRESS))
                .andExpect(jsonPath("$.token").value("JPYC"))
                .andExpect(jsonPath("$.balance").value("1000"))
                .andExpect(jsonPath("$.rawBalance").value("1000000000000000000000"));
    }

    @Test
    void getTokenBalance_invalidAddress_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/chain/token-balance")
                        .param("address", "not-an-address")
                        .param("token", "JPYC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTokenBalance_invalidToken_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/chain/token-balance")
                        .param("address", VALID_ADDRESS)
                        .param("token", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTokenBalance_chainCommunicationError_returns502() throws Exception {
        when(tokenBalanceService.getTokenBalance(any(), any()))
                .thenThrow(new ChainCommunicationException("node down", new IOException()));

        mockMvc.perform(get("/api/v1/chain/token-balance")
                        .param("address", VALID_ADDRESS)
                        .param("token", "JPYC"))
                .andExpect(status().isBadGateway());
    }
}

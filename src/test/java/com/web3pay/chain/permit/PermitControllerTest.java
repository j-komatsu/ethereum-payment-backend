package com.web3pay.chain.permit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3pay.auth.JwtService;
import com.web3pay.config.SecurityConfig;
import com.web3pay.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PermitController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class PermitControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    PermitService permitService;

    @MockBean
    JwtService jwtService;

    private static final String OWNER = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";
    private static final String BEARER = "Bearer test-token";
    private static final long FUTURE_DEADLINE = Instant.now().plusSeconds(1800).getEpochSecond();

    @BeforeEach
    void setUpAuth() {
        when(jwtService.extractAddress("test-token")).thenReturn(OWNER);
    }

    // ------------------------------------------------------------------ GET /api/v1/permit/typed-data

    @Test
    void getTypedData_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/permit/typed-data?paymentOrderId=order-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getTypedData_withAuth_callsServiceWithOwnerAddress() throws Exception {
        PermitTypedData typedData = buildTypedData();
        when(permitService.buildTypedData("order-1", OWNER)).thenReturn(typedData);

        mockMvc.perform(get("/api/v1/permit/typed-data?paymentOrderId=order-1")
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryType").value("Permit"));

        verify(permitService).buildTypedData("order-1", OWNER);
    }

    @Test
    void getTypedData_permitException_returns400() throws Exception {
        when(permitService.buildTypedData(any(), any()))
                .thenThrow(new PermitException("Order not found"));

        mockMvc.perform(get("/api/v1/permit/typed-data?paymentOrderId=order-missing")
                        .header("Authorization", BEARER))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ POST /api/v1/permit/execute

    @Test
    void executePermit_withoutAuth_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "paymentOrderId", "order-1",
                "nonce", "0",
                "deadline", FUTURE_DEADLINE,
                "signature", "0x" + "a".repeat(130)
        ));

        mockMvc.perform(post("/api/v1/permit/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void executePermit_missingPaymentOrderId_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "nonce", "0",
                "deadline", FUTURE_DEADLINE,
                "signature", "0x" + "a".repeat(130)
        ));

        mockMvc.perform(post("/api/v1/permit/execute")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void executePermit_missingSignature_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "paymentOrderId", "order-1",
                "nonce", "0",
                "deadline", FUTURE_DEADLINE
        ));

        mockMvc.perform(post("/api/v1/permit/execute")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void executePermit_withAuth_callsServiceWithOwnerAddress() throws Exception {
        PermitTxResponse response = new PermitTxResponse(
                "order-1", "0x" + "b".repeat(64), "0x" + "c".repeat(64), "CONFIRMED");
        when(permitService.execute(eq("order-1"), eq(OWNER), anyLong(), anyString(), anyString()))
                .thenReturn(response);

        String body = objectMapper.writeValueAsString(Map.of(
                "paymentOrderId", "order-1",
                "nonce", "0",
                "deadline", FUTURE_DEADLINE,
                "signature", "0x" + "a".repeat(130)
        ));

        mockMvc.perform(post("/api/v1/permit/execute")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentOrderId").value("order-1"));

        verify(permitService).execute(eq("order-1"), eq(OWNER), eq(FUTURE_DEADLINE), eq("0"), anyString());
    }

    @Test
    void executePermit_permitException_returns400() throws Exception {
        when(permitService.execute(any(), any(), anyLong(), any(), any()))
                .thenThrow(new PermitException("Deadline has already passed"));

        String body = objectMapper.writeValueAsString(Map.of(
                "paymentOrderId", "order-1",
                "nonce", "0",
                "deadline", FUTURE_DEADLINE,
                "signature", "0x" + "a".repeat(130)
        ));

        mockMvc.perform(post("/api/v1/permit/execute")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Permit の実行に失敗しました"));
    }

    // ------------------------------------------------------------------ helpers

    private PermitTypedData buildTypedData() {
        return new PermitTypedData(
                "Permit",
                new PermitTypedData.Domain("JPY Coin", "1", 137L, "0x431D5dfF03120AFA4bDf332c61A6e1766eF37BF6"),
                Map.of(
                        "EIP712Domain", PermitTypedData.EIP712_DOMAIN_FIELDS,
                        "Permit", PermitTypedData.PERMIT_FIELDS
                ),
                new PermitTypedData.Message(OWNER, "0x" + "b".repeat(40), "1000000", "0", String.valueOf(FUTURE_DEADLINE))
        );
    }
}

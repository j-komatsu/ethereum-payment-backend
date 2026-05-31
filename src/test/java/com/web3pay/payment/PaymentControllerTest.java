package com.web3pay.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3pay.auth.JwtService;
import com.web3pay.config.SecurityConfig;
import com.web3pay.exception.GlobalExceptionHandler;
import com.web3pay.token.StablecoinType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class PaymentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    PaymentService paymentService;

    @MockBean
    JwtService jwtService;

    private static final String OWNER = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";
    private static final String RECEIVER = "0xabc1230000000000000000000000000000000000";
    private static final String BEARER = "Bearer test-token";

    @BeforeEach
    void setUpAuth() {
        when(jwtService.extractAddress("test-token")).thenReturn(OWNER);
    }

    // ------------------------------------------------------------------ POST /api/v1/payments

    @Test
    void createPaymentOrder_validMpmRequest_returns201() throws Exception {
        PaymentOrder order = buildOrder("order-1", PaymentStatus.PENDING);
        when(paymentService.createOrder(any())).thenReturn(order);

        String body = objectMapper.writeValueAsString(Map.of(
                "receiverAddress", RECEIVER,
                "senderAddress", OWNER,
                "amount", "100",
                "token", "JPYC",
                "paymentMode", "MPM"
        ));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("order-1"));
    }

    @Test
    void createPaymentOrder_withoutAuth_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "receiverAddress", RECEIVER,
                "senderAddress", OWNER,
                "amount", "100",
                "token", "JPYC"
        ));

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPaymentOrder_invalidReceiverAddress_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "receiverAddress", "not-an-address",
                "senderAddress", OWNER,
                "amount", "100",
                "token", "JPYC"
        ));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPaymentOrder_negativeAmount_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "receiverAddress", RECEIVER,
                "senderAddress", OWNER,
                "amount", "-1",
                "token", "JPYC"
        ));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPaymentOrder_unknownToken_returns400() throws Exception {
        String body = """
                {"receiverAddress":"%s","senderAddress":"%s","amount":"100","token":"UNKNOWN"}
                """.formatted(RECEIVER, OWNER);

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ GET /api/v1/payments/{id}

    @Test
    void getPaymentOrder_existingOrder_returns200() throws Exception {
        PaymentOrder order = buildOrder("order-42", PaymentStatus.CONFIRMED);
        when(paymentService.getOrder("order-42")).thenReturn(order);

        mockMvc.perform(get("/api/v1/payments/order-42")
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order-42"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void getPaymentOrder_notFound_returns404() throws Exception {
        when(paymentService.getOrder("missing")).thenThrow(new PaymentOrderNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/payments/missing")
                        .header("Authorization", BEARER))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPaymentOrder_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/order-1"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ GET /api/v1/payments

    @Test
    void listPaymentOrders_noFilter_returns200WithPage() throws Exception {
        PaymentOrder order = buildOrder("order-1", PaymentStatus.PENDING);
        when(paymentService.listOrders(isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));

        mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("order-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listPaymentOrders_withStatusFilter_passesStatusToService() throws Exception {
        when(paymentService.listOrders(eq("CONFIRMED"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/payments?status=CONFIRMED")
                        .header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ helpers

    private PaymentOrder buildOrder(String id, PaymentStatus status) {
        return PaymentOrder.builder()
                .id(id)
                .receiverAddress(RECEIVER)
                .senderAddress(OWNER)
                .expectedAmount(new BigDecimal("100"))
                .token(StablecoinType.JPYC)
                .paymentMode(PaymentMode.MPM)
                .status(status)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}

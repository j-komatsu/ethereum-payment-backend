package com.web3pay.exception;

import com.web3pay.auth.SiweException;
import com.web3pay.chain.ChainCommunicationException;
import com.web3pay.payment.PaymentOrderNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SiweException.class)
    ProblemDetail handleSiwe(SiweException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(PaymentOrderNotFoundException.class)
    ProblemDetail handleNotFound(PaymentOrderNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setDetail("Validation failed");
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList());
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setDetail("Validation failed");
        pd.setProperty("errors", ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList());
        return pd;
    }

    @ExceptionHandler(ChainCommunicationException.class)
    ProblemDetail handleChainCommunication(ChainCommunicationException ex) {
        log.error("Ethereum node communication error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Ethereum ノードとの通信に失敗しました");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "パラメータ '" + ex.getName() + "' の値が不正です");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.debug("Request body parse error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "リクエストボディの形式が不正です");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}

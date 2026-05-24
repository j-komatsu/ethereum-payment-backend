package com.example.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EthereumPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(EthereumPaymentApplication.class, args);
    }
}

package com.web3pay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Web3PayApplication {

    public static void main(String[] args) {
        SpringApplication.run(Web3PayApplication.class, args);
    }
}

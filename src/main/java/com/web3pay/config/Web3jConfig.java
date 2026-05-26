package com.web3pay.config;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.concurrent.TimeUnit;

@Configuration
public class Web3jConfig {

    @Value("${web3j.client-address}")
    private String clientAddress;

    @Value("${web3j.polygon-endpoint:${web3j.client-address}}")
    private String polygonEndpoint;

    @Value("${web3j.ethereum-endpoint:${web3j.client-address}}")
    private String ethereumEndpoint;

    @Bean
    public Web3j web3j() {
        return buildWeb3j(clientAddress);
    }

    @Bean("polygonWeb3j")
    public Web3j polygonWeb3j() {
        return buildWeb3j(polygonEndpoint);
    }

    @Bean("ethereumWeb3j")
    public Web3j ethereumWeb3j() {
        return buildWeb3j(ethereumEndpoint);
    }

    private Web3j buildWeb3j(String endpoint) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.NONE))
                .build();
        return Web3j.build(new HttpService(endpoint, httpClient, false));
    }
}

package com.web3pay.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Web3Pay — Ethereum ステーブルコイン決済 API")
                        .version("1.0")
                        .description("""
                                JPYC / USDC / USDT（Polygon PoS）および DAI（Ethereum L1）を使ったステーブルコイン決済バックエンド。

                                ## 認証
                                - SIWE (Sign-In With Ethereum / EIP-4361) でウォレット署名認証
                                - JWT Bearer トークンで保護されたエンドポイントにアクセス

                                ## 主な機能
                                - 支払いオーダー管理（CPM / MPM モード）
                                - EIP-2612 Permit によるガスレス支払い
                                - Sablier Flow ストリーミング決済（サブスク・秒単位課金）
                                - Transfer イベント監視による自動入金検知
                                - ERC-20 残高・ETH 残高照会
                                """))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("SIWE 認証で発行された JWT トークン")));
    }
}

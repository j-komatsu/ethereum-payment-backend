package com.web3pay.chain;

import com.web3pay.token.StablecoinType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chain", description = "Ethereum ノード直接問い合わせ API")
@RestController
@RequestMapping("/api/v1/chain")
@RequiredArgsConstructor
@Validated
public class TokenBalanceController {

    private final TokenBalanceService tokenBalanceService;

    @Operation(summary = "ERC-20トークン残高照会", description = "指定アドレスの ERC-20 トークン残高を返します（JPYC / USDC / USDT / DAI）")
    @GetMapping("/token-balance")
    public ResponseEntity<TokenBalanceResponse> getTokenBalance(
            @Parameter(description = "ウォレットアドレス (0x + 40文字の16進数)", example = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
            @RequestParam
            @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "有効なEthereumアドレス形式（0x + 40文字の16進数）を入力してください")
            String address,

            @Parameter(description = "トークン種別", example = "JPYC")
            @RequestParam
            StablecoinType token) {
        return ResponseEntity.ok(tokenBalanceService.getTokenBalance(address, token));
    }
}

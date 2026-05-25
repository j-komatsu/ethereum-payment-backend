package com.web3pay.chain;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chain", description = "Ethereum ノード直接問い合わせ API")
@RestController
@RequestMapping("/api/v1/chain")
@RequiredArgsConstructor
@Validated
public class ChainController {

    private final ChainService chainService;

    @Operation(summary = "ETH残高照会", description = "指定アドレスの ETH 残高を Wei と ETH 単位で返します")
    @GetMapping("/eth-balance/{address}")
    public ResponseEntity<EthBalanceResponse> getEthBalance(
            @Parameter(description = "Ethereum アドレス (0x + 40文字の16進数)", example = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045")
            @PathVariable
            @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "有効なEthereumアドレス形式（0x + 40文字の16進数）を入力してください")
            String address) {
        return ResponseEntity.ok(chainService.getEthBalance(address));
    }
}

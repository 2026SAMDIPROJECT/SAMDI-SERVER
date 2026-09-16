package com.example.samdi.wallet.controller;

import com.example.samdi.wallet.dto.*;
import com.example.samdi.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// API는 앱이 HTTP 요청을 보내 서버의 wallet 기능을 사용하는 접점이다.
// @RestController는 반환 객체를 JSON 응답으로 변환한다.
@RestController
// 이 컨트롤러의 모든 API 주소 앞에 공통으로 붙는 기본 경로다.
@RequestMapping("/api/users/me/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    // GET은 데이터를 변경하지 않고 조회할 때 사용한다. 현재 잔액과 누적 입출금 합계를 반환한다.
    // @AuthenticationPrincipal의 userId는 로그인 인증 과정에서 전달된 현재 사용자 ID다.
    @GetMapping
    public WalletResponse getWallet(@AuthenticationPrincipal Long userId) {
        return walletService.getSummary(userId);
    }

    // POST는 새로운 데이터를 등록할 때 사용한다. @RequestBody는 요청의 JSON을 TransactionRequest로 변환한다.
    // 입금 내역을 만들고 성공하면 HTTP 201을 반환한다.
    @PostMapping("/transactions/income")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse registerIncome(@AuthenticationPrincipal Long userId,
                                               @RequestBody TransactionRequest request) {
        return walletService.registerIncome(userId, request);
    }

    // 지출 내역 등록 API 요청 금액은 양수로 받고 서버가 지출 금액을 음수로 저장한다.
    @PostMapping("/transactions/outcome")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse registerOutcome(@AuthenticationPrincipal Long userId,
                                                @RequestBody TransactionRequest request) {
        return walletService.registerOutcome(userId, request);
    }

}

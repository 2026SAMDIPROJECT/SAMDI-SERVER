package com.example.samdi.domain.user.controller;

import com.example.samdi.domain.user.dto.request.LoginRequest;
import com.example.samdi.domain.user.dto.request.SignupRequest;
import com.example.samdi.domain.user.dto.request.NicknameRequest;
import com.example.samdi.domain.user.dto.response.LoginResponse;
import com.example.samdi.domain.user.dto.response.SignupResponse;
import com.example.samdi.domain.user.service.UserService;
import com.example.samdi.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import com.example.samdi.domain.user.exception.InvalidCredentialsException;
import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    @Value("${jwt.refresh-token-expiration-minutes}")
    private long refreshTokenExpirationMinutes = 20160;

    @PatchMapping("/me/nickname")
    public ApiResponse<Void> changeNickname(@RequestAttribute("userId") String userId,
                                            @Valid @RequestBody NicknameRequest request) {
        userService.changeNickname(Long.valueOf(userId), request.nickname());
        return ApiResponse.success("닉네임이 변경되었습니다.", null);
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = userService.signup(request);
        return ApiResponse.success("회원가입이 완료되었습니다.", response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(@CookieValue(name = "__Secure-refreshToken", required = false) String refreshToken,
                                                     @RequestHeader(name = "X-Requested-With", required = false) String requestedWith) {
        if (!"XMLHttpRequest".equals(requestedWith)) {
            throw new AccessDeniedException("잘못된 요청입니다.");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidCredentialsException();
        }
        LoginResponse response = userService.refresh(refreshToken);
        return tokenResponse("토큰이 재발급되었습니다.", response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestAttribute("userId") String userId,
                                    @RequestAttribute("sessionId") String sessionId) {
        userService.logout(Long.valueOf(userId), sessionId);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO))
                .body(ApiResponse.success("로그아웃되었습니다.", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return tokenResponse("로그인에 성공했습니다.", response);
    }

    private ResponseEntity<ApiResponse<Void>> tokenResponse(String message, LoginResponse response) {
        return ResponseEntity
                .ok()
                .header(HttpHeaders.AUTHORIZATION,"Bearer "+ response.accessToken())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(response.refreshToken(), Duration.ofMinutes(refreshTokenExpirationMinutes)))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(message, null));
    }

    private String refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from("__Secure-refreshToken", value).httpOnly(true).secure(true)
                .sameSite("Strict").path("/auth/refresh").maxAge(maxAge).build().toString();
    }

}

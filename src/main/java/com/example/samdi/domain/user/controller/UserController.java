package com.example.samdi.domain.user.controller;

import com.example.samdi.domain.user.dto.request.LoginRequest;
import com.example.samdi.domain.user.dto.request.SignupRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestAttribute;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = userService.signup(request);
        return ApiResponse.success("회원가입이 완료되었습니다.", response);
    }

    public record RefreshRequest(@NotBlank String refreshToken) {}

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success("토큰이 재발급되었습니다.", userService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestAttribute("userId") String userId,
                                    @RequestAttribute("sessionId") String sessionId) {
        userService.logout(Long.valueOf(userId), sessionId);
        return ApiResponse.success("로그아웃되었습니다.", null);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return ResponseEntity
                .ok()
                .header(HttpHeaders.AUTHORIZATION,"Bearer "+ response.accessToken())
                .body(ApiResponse.success("로그인에 성공했습니다.",response));
    }


}

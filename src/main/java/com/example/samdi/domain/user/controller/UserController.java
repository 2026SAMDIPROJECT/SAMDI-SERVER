package com.example.samdi.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.example.samdi.domain.user.dto.request.LoginRequest;
import com.example.samdi.domain.user.dto.request.SignupRequest;
import com.example.samdi.domain.user.dto.request.NicknameRequest;
import com.example.samdi.domain.user.dto.request.PasswordChangeRequest;
import com.example.samdi.domain.user.dto.request.WithdrawRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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

@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    @Value("${jwt.refresh-token-expiration-minutes}")
    private long refreshTokenExpirationMinutes = 20160;

    @Tag(name = "내 계정", description = "로그인한 사용자의 닉네임, 비밀번호 및 계정을 관리합니다.")
    @Operation(summary = "닉네임 변경",
            description = "**인증** Bearer 액세스 토큰\n\n**필수 입력** `nickname`. 최대 50자까지 입력할 수 있습니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = NicknameRequest.class),
                            examples = @ExampleObject(value = "{\"nickname\":\"새 닉네임\"}"))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "닉네임 변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "닉네임 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 정보가 유효하지 않음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "닉네임 중복")
    })
    @PatchMapping("/me/nickname")
    public ApiResponse<Void> changeNickname(
            @Parameter(hidden = true) @RequestAttribute("userId") String userId,
            @Valid @RequestBody NicknameRequest request
    ) {
        userService.changeNickname(Long.valueOf(userId), request.nickname());
        return ApiResponse.success("닉네임이 변경되었습니다.", null);
    }

    @Operation(tags = "내 계정", summary = "비밀번호 변경",
            description = "**인증** Bearer 액세스 토큰\n\n**필수 입력** `currentPassword`, `newPassword`, `newPasswordConfirm`. 새 비밀번호는 공백을 제외한 영문·숫자·특수문자 8~20자이며 확인 값과 일치해야 합니다. 성공하면 리프레시 토큰을 폐기합니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = PasswordChangeRequest.class),
                            examples = @ExampleObject(value = "{\"currentPassword\":\"Example123!\",\"newPassword\":\"NewExample123!\",\"newPasswordConfirm\":\"NewExample123!\"}"))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "비밀번호 변경 성공",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "리프레시 토큰 쿠키 삭제")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "새 비밀번호 형식 오류 또는 확인 값 불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보 또는 현재 비밀번호 불일치")
    })
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Parameter(hidden = true) @RequestAttribute("userId") String userId,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        userService.changePassword(Long.valueOf(userId), request);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO))
                .body(ApiResponse.success("비밀번호가 변경되었습니다.", null));
    }

    @Operation(tags = "내 계정", summary = "회원 탈퇴",
            description = "**인증** Bearer 액세스 토큰\n\n**필수 입력** `password`. 비밀번호가 맞으면 계정과 연관된 로그인 세션, 점수 및 업적 진행 기록을 삭제합니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = WithdrawRequest.class),
                            examples = @ExampleObject(value = "{\"password\":\"Example123!\"}"))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원 탈퇴 성공",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "리프레시 토큰 쿠키 삭제")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "비밀번호 누락"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 인증 정보 또는 비밀번호 불일치")
    })
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @Parameter(hidden = true) @RequestAttribute("userId") String userId,
            @Valid @RequestBody WithdrawRequest request
    ) {
        userService.withdraw(Long.valueOf(userId), request.password());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO))
                .body(ApiResponse.success("회원 탈퇴가 완료되었습니다.", null));
    }

    @Operation(tags = "사용자 인증", summary = "이메일 인증 후 회원가입",
            description = "**필수 입력** `email`, `loginId`, `nickname`, `password`, `passwordConfirm`, `verificationToken`\n\n이메일 인증에서 받은 일회용 가입 토큰을 사용합니다. 비밀번호는 공백을 제외한 영문·숫자·특수문자 8~20자이며 확인 값과 일치해야 합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = SignupRequest.class),
                            examples = @ExampleObject(value = "{\"email\":\"player@example.com\",\"loginId\":\"player01\",\"nickname\":\"플레이어\",\"password\":\"Example123!\",\"passwordConfirm\":\"Example123!\",\"verificationToken\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}"))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "가입 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 입력 또는 유효하지 않은 가입 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이메일·로그인 ID·닉네임 중복")
    })
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = userService.signup(request);
        return ApiResponse.success("회원가입이 완료되었습니다.", response);
    }

    @Tag(name = "토큰 세션", description = "토큰을 갱신하거나 로그인 세션을 종료합니다.")
    @Operation(summary = "액세스 토큰 갱신", description = """
            **필수 입력** `__Secure-refreshToken` 쿠키와 `X-Requested-With: XMLHttpRequest` 헤더

            로그인 또는 이전 갱신 응답에서 받은 쿠키를 HTTPS 요청에 포함합니다.
            성공하면 새 액세스 토큰을 반환하고 기존 리프레시 토큰을 교체합니다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 갱신 성공",
                    headers = {
                            @Header(name = HttpHeaders.AUTHORIZATION, description = "Bearer 액세스 토큰"),
                            @Header(name = HttpHeaders.SET_COOKIE, description = "갱신된 리프레시 토큰 쿠키")
                    }),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "리프레시 토큰 누락, 만료 또는 불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "X-Requested-With 헤더 누락 또는 불일치")
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(
            @Parameter(required = true, description = "로그인 또는 이전 갱신 응답에서 받은 리프레시 토큰 쿠키")
            @CookieValue(name = "__Secure-refreshToken", required = false) String refreshToken,
            @Parameter(required = true, description = "요청 구분 헤더", example = "XMLHttpRequest")
            @RequestHeader(name = "X-Requested-With", required = false) String requestedWith
    ) {
        if (!"XMLHttpRequest".equals(requestedWith)) {
            throw new AccessDeniedException("잘못된 요청입니다.");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidCredentialsException();
        }
        LoginResponse response = userService.refresh(refreshToken);
        return tokenResponse("토큰이 재발급되었습니다.", response);
    }

    @Operation(tags = "토큰 세션", summary = "로그인 세션 종료", security = @SecurityRequirement(name = "bearerAuth"),
            description = """
                    **인증** Bearer 액세스 토큰

                    토큰에 해당하는 세션의 리프레시 토큰을 폐기하고 쿠키를 삭제합니다.
                    이미 발급된 액세스 토큰은 만료 시점까지 유효합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공",
                    headers = @Header(name = HttpHeaders.SET_COOKIE, description = "리프레시 토큰 쿠키 삭제")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "액세스 토큰 또는 로그인 세션이 유효하지 않음")
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Parameter(hidden = true) @RequestAttribute("userId") String userId,
            @Parameter(hidden = true) @RequestAttribute("sessionId") String sessionId
    ) {
        userService.logout(Long.valueOf(userId), sessionId);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO))
                .body(ApiResponse.success("로그아웃되었습니다.", null));
    }

    @Operation(tags = "사용자 인증", summary = "로그인",
            description = "**필수 입력** `loginId`, `password`\n\n응답의 `Authorization` 헤더에 액세스 토큰을, `Set-Cookie`에 리프레시 토큰을 반환합니다. 로그인 실패가 누적되면 일시적으로 제한될 수 있습니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = LoginRequest.class),
                            examples = @ExampleObject(value = "{\"loginId\":\"player01\",\"password\":\"Example123!\"}"))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 본문"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 정보 불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "로그인 시도 제한")
    })
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

package com.example.samdi.domain.auth.email;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.example.samdi.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자 인증", description = "이메일 인증, 회원가입 및 로그인에 사용합니다.")
@RestController
@RequestMapping("/auth/email")
public class EmailVerificationController {
    private final EmailVerificationService service;

    public EmailVerificationController(EmailVerificationService service) {
        this.service = service;
    }

    public record SendRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record VerifyRequest(@NotBlank @Email @Size(max = 254) String email,
                                @NotBlank @jakarta.validation.constraints.Pattern(regexp = "[0-9]{6}") String code) {}

    public record VerifyResponse(String verificationToken) {}

    @Operation(tags = "사용자 인증", summary = "이메일 인증번호 확인",
            description = "**필수 입력** `email`, `code`\n\n발송된 6자리 코드를 검증하고 `data.verificationToken`으로 10분 동안 유효한 가입 토큰을 반환합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = VerifyRequest.class),
                            examples = @ExampleObject(value = "{\"email\":\"player@example.com\",\"code\":\"123456\"}"))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "가입 토큰 발급"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "인증번호 불일치 또는 만료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "인증번호 입력 횟수 초과")
    })
    @PostMapping("/verify")
    public ApiResponse<VerifyResponse> verify(@Valid @RequestBody VerifyRequest request) {
        return ApiResponse.success("인증번호가 확인되었습니다.", new VerifyResponse(service.verify(request.email(), request.code())));
    }

    @Operation(tags = "사용자 인증", summary = "이메일 인증번호 발송",
            description = "**필수 입력** `email`\n\n6자리 인증번호를 발송합니다. 인증번호는 5분 동안 유효하며, 같은 주소로 재발송하려면 60초를 기다려야 합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = SendRequest.class),
                            examples = @ExampleObject(value = "{\"email\":\"player@example.com\"}"))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증번호 발송 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이메일 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "재발송 대기 시간 미경과"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "메일 발송 실패")
    })
    @PostMapping("/send")
    public ApiResponse<Void> send(@Valid @RequestBody SendRequest request) {
        service.send(request.email());
        return ApiResponse.success("인증번호가 발송되었습니다.", null);
    }
}

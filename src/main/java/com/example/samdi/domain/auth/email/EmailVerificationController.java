package com.example.samdi.domain.auth.email;

import com.example.samdi.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/verify")
    public ApiResponse<VerifyResponse> verify(@Valid @RequestBody VerifyRequest request) {
        return ApiResponse.success("인증번호가 확인되었습니다.", new VerifyResponse(service.verify(request.email(), request.code())));
    }

    @PostMapping("/send")
    public ApiResponse<Void> send(@Valid @RequestBody SendRequest request) {
        service.send(request.email());
        return ApiResponse.success("인증번호가 발송되었습니다.", null);
    }
}

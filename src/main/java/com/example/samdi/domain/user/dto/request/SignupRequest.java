package com.example.samdi.domain.user.dto.request;

import jakarta.validation.constraints.*;

public record SignupRequest(

        @Email
        @Size(max = 254)
        @NotBlank
        String email,

        @NotBlank
        @Size(min = 8,max = 20, message = "비밀번호는 최소 8자 이상 20자 이하여야 합니다.")
        @Pattern(
                regexp = "^[!-~]+$",
                message = "비밀번호는 영문, 숫자, 특수문자만 입력할 수 있습니다."
        )
        String password,

        @NotBlank
        @Size(max = 20)
        String passwordConfirm,

        @NotBlank
        @Size(max = 50)
        String loginId,

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]{43}")
        String verificationToken,

        @NotBlank
        @Size(max = 50)
        String nickname

) {
    public SignupRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}

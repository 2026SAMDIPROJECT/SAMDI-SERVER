package com.example.samdi.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 20) @Pattern(regexp = "^[!-~]+$") String newPassword,
        @NotBlank @Size(max = 20) String newPasswordConfirm
) {
}

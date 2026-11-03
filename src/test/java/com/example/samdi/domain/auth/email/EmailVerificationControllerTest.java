package com.example.samdi.domain.auth.email;

import com.example.samdi.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmailVerificationControllerTest {
    @Test
    void invalidEmailReturnsBadRequestWithoutSending() throws Exception {
        EmailVerificationService service = mock(EmailVerificationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new EmailVerificationController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/auth/email/send").contentType("application/json")
                        .content("{\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void validRequestCallsService() throws Exception {
        EmailVerificationService service = mock(EmailVerificationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new EmailVerificationController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/auth/email/send").contentType("application/json")
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(service).send("user@example.com");
    }
}

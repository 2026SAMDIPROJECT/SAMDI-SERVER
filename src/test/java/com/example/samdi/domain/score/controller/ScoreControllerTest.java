package com.example.samdi.domain.score.controller;

import com.example.samdi.config.SecurityConfig;
import com.example.samdi.domain.score.service.ScoreService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScoreController.class)
@Import(SecurityConfig.class)
class ScoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScoreService scoreService;

    @Test
    void returns204ForValidScore() throws Exception {
        mockMvc.perform(post("/scores")
                        .with(loginAs("1"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score": 100}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(scoreService).updateHighestScore(1L, 100);
    }

    @Test
    void returns400ForNegativeScore() throws Exception {
        mockMvc.perform(post("/scores")
                        .with(loginAs("1"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score": -1}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(scoreService);
    }

    @Test
    void returns400ForMissingScore() throws Exception {
        mockMvc.perform(post("/scores")
                        .with(loginAs("1"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(scoreService);
    }

    @Test
    void returns404ForUnknownUser() throws Exception {
        doThrow(new EntityNotFoundException("사용자를 찾을 수 없습니다."))
                .when(scoreService).updateHighestScore(1L, 100);

        mockMvc.perform(post("/scores")
                        .with(loginAs("1"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score": 100}
                                """))
                .andExpect(status().isNotFound());

        verify(scoreService).updateHighestScore(1L, 100);
    }

    @Test
    void returns401ForInvalidUserId() throws Exception {
        mockMvc.perform(post("/scores")
                        .with(loginAs("abc"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score": 100}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(scoreService);
    }

    @Test
    void returns403WithoutAuthentication() throws Exception {
        mockMvc.perform(post("/scores")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"score": 100}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(scoreService);
    }

    private RequestPostProcessor loginAs(String userId) {
        return authentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of())
        );
    }
}
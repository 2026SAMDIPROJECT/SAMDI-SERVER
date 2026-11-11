package com.example.samdi.domain.score.controller;

import com.example.samdi.domain.score.service.ScoreService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ScoreControllerTest {

    @Mock
    private ScoreService scoreService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ScoreController(scoreService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returns204ForValidScore() throws Exception {
        loginAs("1");
        mockMvc.perform(post("/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"game1Score": 80, "game2Score": 100}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(scoreService).updateHighestScore(1L, 80, 100);
    }

    @Test
    void returns400ForNegativeScore() throws Exception {
        loginAs("1");
        mockMvc.perform(post("/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"game1Score": -1, "game2Score": 100}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(scoreService);
    }

    @Test
    void returns400ForMissingScore() throws Exception {
        loginAs("1");
        mockMvc.perform(post("/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"game1Score\": 80}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(scoreService);
    }

    @Test
    void returns404ForUnknownUser() throws Exception {
        doThrow(new EntityNotFoundException("사용자를 찾을 수 없습니다."))
                .when(scoreService).updateHighestScore(1L, 80, 100);

        loginAs("1");
        mockMvc.perform(post("/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"game1Score": 80, "game2Score": 100}
                                """))
                .andExpect(status().isNotFound());

        verify(scoreService).updateHighestScore(1L, 80, 100);
    }

    @Test
    void returns401ForInvalidUserId() throws Exception {
        loginAs("abc");
        mockMvc.perform(post("/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"game1Score": 80, "game2Score": 100}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(scoreService);
    }

    @Test
    void returns401WithoutAuthentication() throws Exception {
        mockMvc.perform(post("/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"game1Score": 80, "game2Score": 100}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(scoreService);
    }

    private void loginAs(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of())
        );
    }
}

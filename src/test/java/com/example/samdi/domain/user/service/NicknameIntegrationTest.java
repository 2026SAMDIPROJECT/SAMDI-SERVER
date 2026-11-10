package com.example.samdi.domain.user.service;

import com.example.samdi.domain.auth.email.EmailSender;
import com.example.samdi.domain.user.entity.User;
import com.example.samdi.domain.user.repository.UserRepository;
import com.example.samdi.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NicknameIntegrationTest {
    @Autowired private UserRepository users;
    @Autowired private JwtProvider jwtProvider;
    @MockitoBean private EmailSender sender;
    @LocalServerPort private int port;

    private User createUser() {
        String id = UUID.randomUUID().toString();
        return users.saveAndFlush(User.builder().userName(id).email(id + "@example.invalid")
                .password("hash").nickname(id).build());
    }

    private HttpResponse<String> change(String token, String nickname) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/me/nickname"))
                .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(request.method("PATCH", HttpRequest.BodyPublishers.ofString(
                "{\"nickname\":\"" + nickname + "\"}")).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void authenticatedUserCanChangeOnlyOwnNickname() throws Exception {
        User first = createUser();
        User second = createUser();
        String token = jwtProvider.createAccessToken(first.getId().toString(), "session");
        assertEquals(401, change(null, "changed").statusCode());
        assertEquals(200, change(token, "changed").statusCode());
        assertEquals("changed", users.findById(first.getId()).orElseThrow().getNickname());
        assertEquals(second.getNickname(), users.findById(second.getId()).orElseThrow().getNickname());
        assertEquals(200, change(token, "changed").statusCode());
        assertEquals(400, change(token, " ").statusCode());
        assertEquals(400, change(token, "a".repeat(51)).statusCode());
        var duplicate = change(token, second.getNickname());
        assertEquals(409, duplicate.statusCode());
        assertTrue(duplicate.body().contains("DUPLICATE_NICKNAME"));
        assertEquals("changed", users.findById(first.getId()).orElseThrow().getNickname());
    }
}

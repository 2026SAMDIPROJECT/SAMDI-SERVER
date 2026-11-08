package com.example.samdi.global.security.jwt;

import com.example.samdi.domain.user.controller.UserController;
import com.example.samdi.domain.auth.email.EmailVerificationService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.samdi.domain.user.exception.UserErrorCode;
import com.example.samdi.global.exception.BaseErrorCode;
import com.example.samdi.global.exception.ErrorCode;
import com.example.samdi.global.security.SecurityPaths;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(JwtAuthenticationIntegrationTest.JwtTestConfiguration.class)
class JwtAuthenticationIntegrationTest {

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @LocalServerPort
    private int port;

    @MockitoSpyBean
    private JwtProvider jwtProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Autowired
    private FilterRegistrationBean<JwtAuthenticationFilter> jwtFilter;

    @Autowired
    private ServletContext servletContext;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void securityPathsMatchActualControllerPostMappings() {
        assertControllerPostMapping("login", SecurityPaths.LOGIN);
        assertControllerPostMapping("signup", SecurityPaths.SIGNUP);
    }

    private void assertControllerPostMapping(String methodName, String expectedPath) {
        var mappings = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().equals(UserController.class)
                        && entry.getValue().getMethod().getName().equals(methodName))
                .map(Map.Entry::getKey)
                .toList();

        assertEquals(1, mappings.size(), methodName + " 매핑은 하나여야 합니다.");
        assertEquals(Set.of(expectedPath), mappings.getFirst().getPatternValues());
        assertEquals(Set.of(RequestMethod.POST), mappings.getFirst().getMethodsCondition().getMethods());
    }

    @Test
    void jwtFilterIsRegisteredOnlyOnceInSecurityChain() {
        assertFalse(jwtFilter.isEnabled());
        assertSame(jwtAuthenticationFilter, jwtFilter.getFilter());
        assertEquals(1L, securityFilterChain.getFilters().stream()
                .filter(JwtAuthenticationFilter.class::isInstance).count());
        assertTrue(securityFilterChain.getFilters().contains(jwtAuthenticationFilter));
        assertTrue(servletContext.getFilterRegistrations().values().stream()
                .noneMatch(registration -> registration.getClassName()
                        .equals(JwtAuthenticationFilter.class.getName())));
    }

    @Test
    void validJwtReachesProtectedApiWithUserId() throws Exception {
        var request = request("/test/auth")
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken("42", "test-session"))
                .GET().build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("인증된 사용자 ID: 42", response.body());
    }

    @Test
    void protectedApiWithoutJwtReturns401() throws Exception {
        var response = client.send(request("/test/auth").GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertErrorResponse(response, ErrorCode.UNAUTHORIZED);
    }

    @Test
    void protectedApiWithInvalidJwtReturns401() throws Exception {
        var response = client.send(request("/test/auth")
                        .header("Authorization", "Bearer not-a-jwt").GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertErrorResponse(response, ErrorCode.INVALID_TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/login", "/auth/signup", "/auth/email/send"})
    void publicAuthEndpointsReachValidationWithoutJwt(String path) throws Exception {
        var response = client.send(request(path).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString());

        // Invalid input proves MVC was reached, without creating a user or needing credentials.
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("VALIDATION_ERROR"));
    }

    @Test
    void validRequestParsesJwtOnlyOnce() throws Exception {
        String token = jwtProvider.createAccessToken("43", "test-session");
        var response = client.send(request("/test/auth")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("인증된 사용자 ID: 43", response.body());
        verify(jwtProvider, times(1)).getAccessIdentity(token);
        verify(jwtAuthenticationFilter, times(1)).doFilter(any(), any(), any());
    }

    @Test
    void anonymousRequestReachingSecurityEntryPointReturns401() throws Exception {
        // Non-CORS OPTIONS bypasses the JWT filter, but still requires authentication in Security.
        var response = client.send(request("/test/auth")
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());

        assertErrorResponse(response, ErrorCode.UNAUTHORIZED);
    }

    @Test
    void authenticatedDownstreamAccessDeniedReturns403() throws Exception {
        var response = client.send(request("/test/filter-denied")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken("42", "test-session"))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertErrorResponse(response, ErrorCode.FORBIDDEN);
        assertTrue(response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE).isEmpty());
    }

    @Test
    void authenticatedControllerAccessDeniedReturns403() throws Exception {
        var response = client.send(request("/test/controller-denied")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken("42", "test-session"))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertErrorResponse(response, ErrorCode.FORBIDDEN);
    }

    @Test
    void signupThenLoginTokenGrantsAccessToProtectedApi() throws Exception {
        String email = "jwt-" + UUID.randomUUID() + "@example.com";
        String signupBody = objectMapper.writeValueAsString(Map.of(
                "email", email, "password", "Test1234!",
                "passwordConfirm", "Test1234!", "loginId", "jwt-tester", "verificationToken", "A".repeat(43)
        ));
        var signupResponse = client.send(request("/auth/signup")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(signupBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, signupResponse.statusCode());
        var signupJson = objectMapper.readTree(signupResponse.body());
        assertTrue(signupJson.get("success").asBoolean());
        assertEquals(email, signupJson.get("data").get("email").asString());
        assertFalse(signupJson.get("data").hasNonNull("userId"));

        String loginBody = objectMapper.writeValueAsString(Map.of(
                "loginId", "jwt-tester", "verificationToken", "A".repeat(43), "password", "Test1234!"
        ));
        var loginResponse = client.send(request("/auth/login")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginResponse.statusCode());
        var loginJson = objectMapper.readTree(loginResponse.body());
        assertTrue(loginJson.get("success").asBoolean());
        assertFalse(loginJson.has("data"));
        String token = loginResponse.headers().firstValue("Authorization").orElseThrow().substring(7);
        String cookie = loginResponse.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertTrue(cookie.contains("Path=/auth/refresh"));
        assertTrue(cookie.contains("Max-Age="));
        assertFalse(token.isBlank());

        var protectedResponse = client.send(request("/test/auth")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, protectedResponse.statusCode());
        assertEquals("인증된 사용자 ID: " + jwtProvider.getUserId(token), protectedResponse.body());

        var anonymousResponse = client.send(request("/test/auth").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertErrorResponse(anonymousResponse, ErrorCode.UNAUTHORIZED);
    }

    @Test
    void refreshRequiresCsrfHeaderAndCookie() throws Exception {
        var missingHeader = client.send(request("/auth/refresh")
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, missingHeader.statusCode());
        var missingCookie = client.send(request("/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, missingCookie.statusCode());
    }

    @Test
    void staleAccessCannotLogoutNewSessionAndRotationKeepsSession() throws Exception {
        String loginId = "session-" + UUID.randomUUID();
        String signupBody = objectMapper.writeValueAsString(Map.of(
                "email", loginId + "@example.com", "loginId", loginId,
                "password", "Test1234!", "passwordConfirm", "Test1234!", "verificationToken", "A".repeat(43)));
        var signup = client.send(request("/auth/signup").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(signupBody)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, signup.statusCode());
        String loginBody = objectMapper.writeValueAsString(Map.of("loginId", loginId, "password", "Test1234!"));
        var first = client.send(request("/auth/login").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody)).build(), HttpResponse.BodyHandlers.ofString());
        var second = client.send(request("/auth/login").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, first.statusCode());
        assertEquals(200, second.statusCode());
        String firstAccess = first.headers().firstValue("Authorization").orElseThrow();
        String secondAccess = second.headers().firstValue("Authorization").orElseThrow();
        String secondCookie = second.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
        var oldLogout = client.send(request("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, firstAccess)
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, oldLogout.statusCode());
        var missingCsrfHeader = client.send(request("/auth/refresh").header("Cookie", secondCookie)
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, missingCsrfHeader.statusCode());
        var refreshed = client.send(request("/auth/refresh").header("Cookie", secondCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, refreshed.statusCode());
        assertFalse(objectMapper.readTree(refreshed.body()).has("data"));
        assertTrue(refreshed.headers().firstValue("Authorization").orElseThrow().startsWith("Bearer "));
        String rotatedCookie = refreshed.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
        assertFalse(secondCookie.equals(rotatedCookie));
        var reused = client.send(request("/auth/refresh").header("Cookie", secondCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, reused.statusCode());
        var logout = client.send(request("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, secondAccess)
                        .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, logout.statusCode());
        assertTrue(logout.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
        var rejected = client.send(request("/auth/refresh").header("Cookie", rotatedCookie)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, rejected.statusCode());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unknownLoginIdAndWrongPasswordKeepSame401Response(boolean registered) throws Exception {
        String email = "login-failure-" + UUID.randomUUID() + "@example.com";
        if (registered) {
            String signupBody = objectMapper.writeValueAsString(Map.of(
                    "email", email, "password", "Correct1234!",
                    "passwordConfirm", "Correct1234!", "loginId", "login-tester", "verificationToken", "A".repeat(43)
            ));
            var signupResponse = client.send(request("/auth/signup")
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .POST(HttpRequest.BodyPublishers.ofString(signupBody)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(201, signupResponse.statusCode());
        }

        String loginBody = objectMapper.writeValueAsString(Map.of(
                "loginId", "login-tester", "verificationToken", "A".repeat(43), "password", "Wrong1234!"
        ));
        var response = client.send(request("/auth/login")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody)).build(),
                HttpResponse.BodyHandlers.ofString());

        assertErrorResponse(response, UserErrorCode.INVALID_CREDENTIALS);
        assertEquals(401, response.statusCode());
        assertEquals("아이디 또는 비밀번호가 올바르지 않습니다.",
                objectMapper.readTree(response.body()).get("message").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"loginId\":\"\",\"password\":\"Test1234!\"}",
            "{\"password\":\"Test1234!\"}",
            "{\"loginId\":\"tester\"}",
            "{\"loginId\":\"   \" ,\"password\":\"Test1234!\"}",
            "{\"loginId\":\"tester\",\"password\":\"\"}"
    })
    void invalidLoginRequestKeeps400Response(String body) throws Exception {
        var response = client.send(request("/auth/login")
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        var json = objectMapper.readTree(response.body());
        assertFalse(json.get("success").asBoolean());
        assertEquals("VALIDATION_ERROR", json.get("error").get("code").asString());
        assertTrue(!json.hasNonNull("data"));
        assertFalse(json.get("error").get("details").isEmpty());
    }

    private void assertErrorResponse(HttpResponse<String> response, BaseErrorCode errorCode) throws Exception {
        assertEquals(errorCode.getHttpStatus().value(), response.statusCode(), response.body());
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(
                response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow()
        )));
        var body = objectMapper.readTree(response.body());
        assertFalse(body.get("success").asBoolean());
        assertEquals(errorCode.getCode(), body.get("error").get("code").asString());
        assertEquals(errorCode.getMessage(), body.get("message").asString());
        assertTrue(!body.hasNonNull("data"));
        assertTrue(body.get("error").get("details").isArray());
        assertTrue(body.get("error").get("details").isEmpty());
        if (errorCode == ErrorCode.UNAUTHORIZED || errorCode == ErrorCode.INVALID_TOKEN) {
            assertEquals("Bearer", response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE).orElseThrow());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {

        @Bean
        FilterRegistrationBean<Filter> testAccessDeniedFilter() {
            // Simulate a downstream authorization denial without replacing the production SecurityFilterChain.
            Filter filter = (request, response, chain) -> {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                assertNotNull(authentication);
                assertTrue(authentication.isAuthenticated());
                throw new AccessDeniedException("test-only denied resource");
            };
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
            registration.setUrlPatterns(List.of("/test/filter-denied"));
            registration.setOrder(Ordered.LOWEST_PRECEDENCE);
            return registration;
        }

        @Bean
        JwtTestController jwtTestController() {
            return new JwtTestController();
        }
    }

    @TestComponent
    @RestController
    static class JwtTestController {

        @GetMapping("/test/controller-denied")
        public void controllerDenied() {
            throw new AccessDeniedException("test-only denied controller");
        }

        @GetMapping("/test/auth")
        public String testAuth(@RequestAttribute("userId") String userId) {
            return "인증된 사용자 ID: " + userId;
        }
    }

    @Test
    void swaggerDocumentationRemainsPublic() throws Exception {
        var response = client.send(request("/v3/api-docs").GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("/auth/signup"));
        assertTrue(response.body().contains("/auth/login"));
    }

    @Test
    void swaggerUiRemainsPublic() throws Exception {
        var response = client.send(request("/swagger-ui/index.html").GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
    }
}

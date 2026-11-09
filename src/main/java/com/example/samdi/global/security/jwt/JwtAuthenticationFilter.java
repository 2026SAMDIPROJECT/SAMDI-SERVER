package com.example.samdi.global.security.jwt;

import com.example.samdi.global.exception.ErrorCode;
import com.example.samdi.global.security.SecurityErrorResponseWriter;
import com.example.samdi.global.security.SecurityPaths;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, SecurityErrorResponseWriter securityErrorResponseWriter) {
        this.jwtProvider = jwtProvider;
        this.securityErrorResponseWriter = securityErrorResponseWriter;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            securityErrorResponseWriter.writeErrorResponse(response,ErrorCode.UNAUTHORIZED);
            return;
        }
        String token = authorization.substring(7);
        JwtProvider.TokenIdentity identity;
        try {
            identity = jwtProvider.getAccessIdentity(token);
        } catch (JwtException | IllegalArgumentException e) {
            securityErrorResponseWriter.writeErrorResponse(response,ErrorCode.INVALID_TOKEN);
            return;
        }

        String userId = identity.userId();
        JwtAuthentication authentication = new JwtAuthentication(userId);

        request.setAttribute("userId",userId);
        request.setAttribute("sessionId", identity.sessionId());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request,response);
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals(SecurityPaths.SIGNUP)
                || path.equals(SecurityPaths.LOGIN) || path.equals("/auth/refresh") || path.equals("/auth/email/send") || path.equals("/auth/email/verify") || "OPTIONS".equals(request.getMethod())
                || path.equals("/swagger-ui.html") || path.startsWith("/swagger-ui/")
                || path.equals("/v3/api-docs") || path.startsWith("/v3/api-docs/");
    }


}

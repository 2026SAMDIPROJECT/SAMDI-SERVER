package com.example.samdi.global.security.jwt;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

public class JwtAuthentication extends UsernamePasswordAuthenticationToken{

    public JwtAuthentication(String userId) {
        super(userId,null,List.of());
    }
}

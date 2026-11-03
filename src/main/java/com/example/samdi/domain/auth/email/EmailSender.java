package com.example.samdi.domain.auth.email;

public interface EmailSender {
    void send(String email, String code);
}

package com.example.samdi.domain.auth.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailSender implements EmailSender {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, @Value("${spring.mail.username}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("[SAMDI] 이메일 인증번호");
        message.setText("인증번호는 " + code + "입니다. 5분 동안 유효합니다.");
        mailSender.send(message);
    }
}

package com.example.samdi.domain.auth.email;

import com.example.samdi.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Base64;
import java.security.MessageDigest;
import java.util.Locale;

@Service
public class EmailVerificationService {
    private final EmailVerificationRepository repository;
    private final EmailSender sender;
    private final String secret;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(EmailVerificationRepository repository, EmailSender sender,
                                    @Value("${email.verification.secret}") String secret) {
        this.repository = repository;
        this.sender = sender;
        this.secret = secret;
    }

    @Transactional
    public void send(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        repository.ensureRow(normalized, LocalDateTime.now().minusMinutes(2));
        var existing = repository.findForUpdate(normalized);
        LocalDateTime now = LocalDateTime.now();
        if (existing.isPresent() && existing.get().getLastSentAt().plusSeconds(60).isAfter(now)) {
            throw new EmailSendException(ErrorCode.EMAIL_RESEND_TOO_SOON);
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1000000));
        String hash = hash(normalized, code);
        if (existing.isPresent()) {
            existing.get().update(hash, now);
        } else {
            repository.saveAndFlush(new EmailVerification(normalized, hash, now));
        }
        try {
            sender.send(normalized, code);
        } catch (RuntimeException e) {
            throw new EmailSendException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    @Transactional(noRollbackFor = EmailSendException.class)
    public String verify(String email, String code) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        EmailVerification verification = repository.findForUpdate(normalized)
                .orElseThrow(() -> new EmailSendException(ErrorCode.EMAIL_CODE_EXPIRED));
        if (verification.getAttempts() >= 5) {
            throw new EmailSendException(ErrorCode.EMAIL_ATTEMPTS_EXCEEDED);
        }
        if (verification.getCodeHash().isEmpty() || !verification.getCodeExpiresAt().isAfter(LocalDateTime.now())) {
            throw new EmailSendException(ErrorCode.EMAIL_CODE_EXPIRED);
        }
        if (!MessageDigest.isEqual(verification.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(normalized, code).getBytes(StandardCharsets.UTF_8))) {
            verification.recordFailure();
            throw new EmailSendException(verification.getAttempts() >= 5
                    ? ErrorCode.EMAIL_ATTEMPTS_EXCEEDED : ErrorCode.EMAIL_CODE_MISMATCH);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String proof = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        verification.verify(hash(normalized, proof), LocalDateTime.now());
        return proof;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void consumeProof(String email, String proof) {
        if (email == null || proof == null || !proof.matches("[A-Za-z0-9_-]{43}")) {
            throw new EmailSendException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        EmailVerification verification = repository.findForUpdate(normalized)
                .orElseThrow(() -> new EmailSendException(ErrorCode.EMAIL_NOT_VERIFIED));
        if (verification.getProofHash() == null || verification.getProofExpiresAt() == null
                || !verification.getProofExpiresAt().isAfter(LocalDateTime.now())
                || !MessageDigest.isEqual(verification.getProofHash().getBytes(StandardCharsets.UTF_8),
                        hash(normalized, proof).getBytes(StandardCharsets.UTF_8))) {
            throw new EmailSendException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        verification.consumeProof();
    }

    private String hash(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("인증번호 해시 처리에 실패했습니다.", e);
        }
    }
}

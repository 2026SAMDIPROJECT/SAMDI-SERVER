package com.example.samdi.domain.auth.email;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_verifications")
public class EmailVerification {
    @Id
    @Column(length = 254)
    private String email;
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;
    @Column(name = "code_expires_at", nullable = false)
    private LocalDateTime codeExpiresAt;
    @Column(name = "last_sent_at", nullable = false)
    private LocalDateTime lastSentAt;

    @Column(nullable = false)
    private int attempts;
    @Column(name = "proof_hash", length = 64)
    private String proofHash;
    @Column(name = "proof_expires_at")
    private LocalDateTime proofExpiresAt;

    protected EmailVerification() {}

    public EmailVerification(String email, String codeHash, LocalDateTime now) {
        this.email = email;
        update(codeHash, now);
    }

    public void update(String codeHash, LocalDateTime now) {
        this.codeHash = codeHash;
        this.lastSentAt = now;
        this.codeExpiresAt = now.plusMinutes(5);
        this.attempts = 0;
        this.proofHash = null;
        this.proofExpiresAt = null;
    }

    public String getCodeHash() { return codeHash; }
    public LocalDateTime getCodeExpiresAt() { return codeExpiresAt; }
    public int getAttempts() { return attempts; }
    public void recordFailure() {
        attempts++;
        if (attempts >= 5) {
            codeHash = "";
        }
    }
    public void verify(String proofHash, LocalDateTime now) {
        this.codeHash = "";
        this.proofHash = proofHash;
        this.proofExpiresAt = now.plusMinutes(10);
    }

    public String getProofHash() { return proofHash; }
    public LocalDateTime getProofExpiresAt() { return proofExpiresAt; }
    public void consumeProof() {
        this.proofHash = null;
        this.proofExpiresAt = null;
    }

    public LocalDateTime getLastSentAt() {
        return lastSentAt;
    }
}

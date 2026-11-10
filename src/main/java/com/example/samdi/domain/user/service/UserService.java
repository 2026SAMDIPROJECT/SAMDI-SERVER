package com.example.samdi.domain.user.service;

import com.example.samdi.domain.auth.entity.RefreshToken;
import com.example.samdi.domain.auth.email.EmailVerificationService;
import com.example.samdi.domain.auth.repository.RefreshTokenRepository;
import com.example.samdi.domain.user.dto.request.LoginRequest;
import com.example.samdi.domain.user.dto.request.SignupRequest;
import com.example.samdi.domain.user.dto.request.PasswordChangeRequest;
import com.example.samdi.domain.user.dto.response.LoginResponse;
import com.example.samdi.domain.user.dto.response.SignupResponse;
import com.example.samdi.domain.user.entity.User;
import com.example.samdi.domain.user.entity.LoginAttempt;
import com.example.samdi.domain.user.exception.DuplicateEmailException;
import com.example.samdi.domain.user.exception.DuplicateLoginIdException;
import com.example.samdi.domain.user.exception.InvalidCredentialsException;
import com.example.samdi.domain.user.exception.PasswordMismatchException;
import com.example.samdi.domain.user.exception.DuplicateNicknameException;
import com.example.samdi.domain.user.exception.UserNotFoundException;
import com.example.samdi.domain.user.exception.IncorrectPasswordException;
import com.example.samdi.domain.user.exception.NewPasswordMismatchException;
import com.example.samdi.domain.user.repository.UserRepository;
import com.example.samdi.global.security.jwt.JwtProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationService emailVerificationService;
    private final LoginAttemptService loginAttemptService;
    @Value("${samdi.security.dummy-password-hash}")
    private String dummyPasswordHash;
    private static final String EMAIL_UNIQUE_CONSTRAINT = "email";
    private static final String USER_NAME_UNIQUE_CONSTRAINT = "uq_users_login_id";
    private static final String NICKNAME_UNIQUE_CONSTRAINT = "user_id";

    public SignupResponse signup(SignupRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!request.password().equals(request.passwordConfirm())) {
            throw new PasswordMismatchException();
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        if (userRepository.findByUserName(request.loginId()).isPresent()) {
            throw new DuplicateLoginIdException();
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new DuplicateNicknameException();
        }
        emailVerificationService.consumeProof(email, request.verificationToken());
        try {
            User user = User
                    .builder()
                    .email(email)
                    .userName(request.loginId())
                    .nickname(request.nickname())
                    .password(passwordEncoder.encode(request.password()))
                    .build();
            User savedUser = userRepository.saveAndFlush(user);
            return new SignupResponse(savedUser.getEmail(),savedUser.getUserName());
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateViolation(e, EMAIL_UNIQUE_CONSTRAINT)) {
                throw new DuplicateEmailException();
            }
            if (isDuplicateViolation(e, USER_NAME_UNIQUE_CONSTRAINT)) {
                throw new DuplicateLoginIdException();
            }
            if (isDuplicateViolation(e, NICKNAME_UNIQUE_CONSTRAINT)) {
                throw new DuplicateNicknameException();
            }
            throw e;
        }
    }

    public void changeNickname(Long userId, String nickname) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.changeNickname(nickname);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateViolation(e, NICKNAME_UNIQUE_CONSTRAINT)) {
                throw new DuplicateNicknameException();
            }
            throw e;
        }
    }

    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(UserNotFoundException::new);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IncorrectPasswordException();
        }
        if (!request.newPassword().equals(request.newPasswordConfirm())) {
            throw new NewPasswordMismatchException();
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.findByUserIdForUpdate(userId).ifPresent(refreshTokenRepository::delete);
    }

    public String hashToken(String refreshToken) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
        byte[] hashBytes = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
        String tokenHash = HexFormat.of().formatHex(hashBytes);
        return tokenHash;
    }

    //데이터베이스에서 나는 오류가 이메일 때문인지 확인
    private boolean isDuplicateViolation(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                if (matchesConstraint(name, constraintName)) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    //제약조건 오류가 이메일 중복때문에 발생했는지 확인
    private boolean matchesConstraint(String constraintName, String expectedConstraintName) {
        if (constraintName == null) {
            return false;
        }

        String name = constraintName.toLowerCase(Locale.ROOT);
        int indexDescription = name.indexOf(" index ");
        if (indexDescription >= 0) {
            name = name.substring(0, indexDescription);
        }

        int qualifier = name.lastIndexOf('.');
        if (qualifier >= 0 && indexDescription < 0) {
            String namespace = name.substring(0, qualifier);
            if (!"users".equals(namespace) && !namespace.endsWith(".users") && !"public".equals(namespace)) {
                return false;
            }
        }

        String localName = name.substring(qualifier + 1);
        return expectedConstraintName.equals(localName);
    }

    public LoginResponse refresh(String token) {
        Long userId;
        JwtProvider.TokenIdentity identity;
        try {
            identity = jwtProvider.getRefreshIdentity(token);
            userId = Long.valueOf(identity.userId());
        } catch (RuntimeException e) {
            throw new InvalidCredentialsException();
        }
        RefreshToken stored = refreshTokenRepository.findByUserIdForUpdate(userId)
                .orElseThrow(InvalidCredentialsException::new);
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())
                || !stored.getSessionId().equals(identity.sessionId())
                || !MessageDigest.isEqual(stored.getTokenHash().getBytes(StandardCharsets.UTF_8),
                        hashToken(token).getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidCredentialsException();
        }
        String sessionId = stored.getSessionId();
        String refreshToken = jwtProvider.createRefreshToken(userId.toString(), sessionId);
        stored.updateToken(hashToken(refreshToken), sessionId, jwtProvider.getRefreshTokenExpiration(refreshToken));
        return new LoginResponse(jwtProvider.createAccessToken(userId.toString(), sessionId), refreshToken);
    }

    public void logout(Long userId, String sessionId) {
        RefreshToken stored = refreshTokenRepository.findByUserIdForUpdate(userId)
                .orElseThrow(InvalidCredentialsException::new);
        if (sessionId == null || !stored.getSessionId().equals(sessionId)) {
            throw new InvalidCredentialsException();
        }
        refreshTokenRepository.delete(stored);
    }

    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginResponse login(LoginRequest request) {
        LoginAttempt attempt = loginAttemptService.begin(request.loginId());
        Optional<User> optionalUser = userRepository.findByUserNameForUpdate(request.loginId());
        if (optionalUser.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            loginAttemptService.fail(attempt);
            throw new InvalidCredentialsException();
        }
        User user = optionalUser.get();
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            loginAttemptService.fail(attempt);
            throw new InvalidCredentialsException();
        }
        loginAttemptService.success(attempt);
        Optional<RefreshToken> existingToken = refreshTokenRepository.findByUserIdForUpdate(user.getId());
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = jwtProvider.createRefreshToken(user.getId().toString(),sessionId);
        String tokenHash = hashToken(refreshToken);
        LocalDateTime expiresAt = jwtProvider.getRefreshTokenExpiration(refreshToken);
        if (existingToken.isEmpty()) {
            RefreshToken newToken = RefreshToken.builder()
                    .userId(user.getId())
                    .tokenHash(tokenHash)
                    .sessionId(sessionId)
                    .expiresAt(expiresAt)
                    .build();
            refreshTokenRepository.save(newToken);
        } else {
            RefreshToken token = existingToken.get();
            token.updateToken(tokenHash,sessionId,expiresAt);
        }
        String accessToken = jwtProvider.createAccessToken(user.getId().toString(),sessionId);
        return new LoginResponse(accessToken, refreshToken);
    }

}

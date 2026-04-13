package com.social.backend.components.auth.service.impl;

import com.social.backend.common.exception.DuplicateResourceException;
import com.social.backend.components.auth.dto.LoginRequest;
import com.social.backend.components.auth.dto.LoginResponse;
import com.social.backend.components.auth.dto.RegisterRequest;
import com.social.backend.components.auth.dto.TokenRefreshResponse;
import com.social.backend.components.auth.entity.RefreshToken;
import com.social.backend.components.auth.exception.TokenRefreshException;
import com.social.backend.components.auth.service.AuthService;
import com.social.backend.components.auth.service.RefreshTokenService;
import com.social.backend.components.email.service.EmailService;
import com.social.backend.components.user.dto.UserResponse;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import com.social.backend.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtUtil jwtUtil,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           RefreshTokenService refreshTokenService,
                           EmailService emailService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.emailService = emailService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        try {
            // Trim spazi e lowercase per il lookup
            String cleanUsername = request.getUsername().trim();

            // Recupera utente case-insensitive
            User user = userRepository.findByUsernameIgnoreCase(cleanUsername)
                    .orElseThrow(() -> new BadCredentialsException("Username o password errati"));

            // Controlla se email è verificata
            if (!user.isEmailVerified()) {
                throw new DisabledException("EMAIL_NOT_VERIFIED");
            }

            // Autentica con l'username reale (quello salvato nel DB)
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            user.getUsername(), // usa username reale dal DB
                            request.getPassword()
                    )
            );

            // Genera token JWT con username reale
            String token = jwtUtil.generateToken(user.getUsername());

            // Genera refresh token
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

            return LoginResponse.builder()
                    .token(token)
                    .refreshToken(refreshToken.getToken())
                    .type("Bearer")
                    .user(buildUserResponse(user))
                    .build();

        } catch (BadCredentialsException | DisabledException e) {
            throw e;
        } catch (Exception e) {
            throw new BadCredentialsException("Username o password errati");
        }
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();

        // Verifica duplicati (case-insensitive)
        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            throw new DuplicateResourceException("Username già esistente: " + username);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateResourceException("Email già esistente: " + email);
        }

        // Genera token di verifica
        String verificationToken = UUID.randomUUID().toString();
        LocalDateTime tokenExpiry = LocalDateTime.now().plusHours(24);

        // Crea nuovo utente NON verificato
        User newUser = new User(
                username,
                email,
                passwordEncoder.encode(request.getPassword()),
                request.getBio(),
                request.getAvatarUrl()
        );
        newUser.setEmailVerified(false);
        newUser.setVerificationToken(verificationToken);
        newUser.setVerificationTokenExpiry(tokenExpiry);

        User savedUser = userRepository.save(newUser);

        // Invia email di verifica
        emailService.sendVerificationEmail(email, username, verificationToken);

        UserResponse userResponse = buildUserResponse(savedUser);

        return LoginResponse.builder()
                .token(null)
                .refreshToken(null)
                .type("pending_verification")
                .user(userResponse)
                .build();
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Token di verifica non valido"));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Email già verificata");
        }

        if (user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("TOKEN_EXPIRED");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("Utente non trovato"));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Email già verificata");
        }

        String newToken = UUID.randomUUID().toString();
        user.setVerificationToken(newToken);
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), newToken);
    }

    @Override
    public TokenRefreshResponse refreshToken(String requestRefreshToken) {
        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String newAccessToken = jwtUtil.generateToken(user.getUsername());
                    return new TokenRefreshResponse(newAccessToken, requestRefreshToken);
                })
                .orElseThrow(() -> new TokenRefreshException(requestRefreshToken,
                        "Refresh token non trovato nel database"));
    }

    @Override
    @Transactional
    public void logout(Long userId) {
        refreshTokenService.deleteByUserId(userId);
    }

    private UserResponse buildUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .bio(user.getBio())
                .displayName(user.getDisplayName())
                .website(user.getWebsite())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .role(user.getRole() != null ? user.getRole() : "USER")
                .banned(user.isBanned())
                .emailVerified(user.isEmailVerified())
                .build();
    }

    @Override
    @Transactional
    public void requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new RuntimeException("EMAIL_NOT_FOUND"));

        // Blocca il reset per account Google-only (nessuna password)
        if (user.getPasswordHash() != null && user.getPasswordHash().startsWith("OAUTH2_NO_PASSWORD_")) {
            throw new RuntimeException("OAUTH2_ACCOUNT");
        }

        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), token);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new RuntimeException("INVALID_TOKEN"));

        if (user.getPasswordResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("TOKEN_EXPIRED");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

        if (user.getPasswordHash() != null && user.getPasswordHash().startsWith("OAUTH2_NO_PASSWORD_")) {
            throw new RuntimeException("OAUTH2_ACCOUNT");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new RuntimeException("WRONG_PASSWORD");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
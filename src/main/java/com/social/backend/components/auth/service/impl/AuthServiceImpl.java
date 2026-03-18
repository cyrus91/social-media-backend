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
import com.social.backend.components.user.dto.UserResponse;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import com.social.backend.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtUtil jwtUtil,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        try {
            // Autentica l'utente
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // Genera token JWT
            String token = jwtUtil.generateToken(request.getUsername());

            // Recupera utente
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new UsernameNotFoundException("Utente non trovato"));

            // Genera refresh token
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

            // Crea UserResponse
            UserResponse userResponse = new UserResponse();
            userResponse.setId(user.getId());
            userResponse.setUsername(user.getUsername());
            userResponse.setEmail(user.getEmail());
            userResponse.setBio(user.getBio());
            userResponse.setAvatarUrl(user.getAvatarUrl());
            userResponse.setCreatedAt(user.getCreatedAt());

            // Restituisci risposta con token e dati utente
            return LoginResponse.builder()
                    .token(token)
                    .refreshToken(refreshToken.getToken())
                    .type("Bearer")
                    .user(userResponse)
                    .build();

        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Username o password errati");
        }
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {

        // TRIM per evitare spazi
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();

        // Verifica che username non esista già
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new DuplicateResourceException("Username già esistente: " + request.getUsername());
        }

        // Verifica che email non esista già
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateResourceException("Email già esistente: " + request.getEmail());
        }

        // Crea nuovo utente
        User newUser = new User(
                username,
                email,
                passwordEncoder.encode(request.getPassword()),  // Hash password!
                request.getBio(),
                request.getAvatarUrl()
        );

        // Salva nel database
        User savedUser = userRepository.save(newUser);

        // Genera token JWT
        String token = jwtUtil.generateToken(savedUser.getUsername());

        // Genera refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(savedUser.getId());

        // Crea UserResponse
        UserResponse userResponse = new UserResponse();
        userResponse.setId(savedUser.getId());
        userResponse.setUsername(savedUser.getUsername());
        userResponse.setEmail(savedUser.getEmail());
        userResponse.setBio(savedUser.getBio());
        userResponse.setAvatarUrl(savedUser.getAvatarUrl());
        userResponse.setCreatedAt(savedUser.getCreatedAt());

        // Restituisci risposta con token (auto-login dopo registrazione)
        return LoginResponse.builder()
                .token(token)
                .refreshToken(refreshToken.getToken())
                .type("Bearer")
                .user(userResponse)
                .build();
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
}
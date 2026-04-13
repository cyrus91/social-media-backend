package com.social.backend.components.auth.oauth2;

import com.social.backend.components.user.enums.Role;

import com.social.backend.components.auth.service.RefreshTokenService;
import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import com.social.backend.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String googleId = oAuth2User.getAttribute("sub");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        // Cerca utente per googleId o email
        Optional<User> existing = userRepository.findByGoogleId(googleId);
        if (existing.isEmpty()) {
            existing = userRepository.findByEmail(email);
        }

        User user;
        if (existing.isPresent()) {
            // Utente già esistente — aggiorna googleId e avatar se mancanti
            user = existing.get();
            if (user.getGoogleId() == null) {
                user.setGoogleId(googleId);
            }
            if (user.getAvatarUrl() == null && picture != null) {
                user.setAvatarUrl(picture);
            }
            if (!user.isEmailVerified()) {
                user.setEmailVerified(true);
            }
            userRepository.save(user);
        } else {
            // Nuovo utente — genera username univoco da nome Google
            String baseUsername = generateUsername(name, email);
            String username = ensureUniqueUsername(baseUsername);

            user = User.builder()
                    .username(username)
                    .email(email)
                    .passwordHash("OAUTH2_NO_PASSWORD_" + UUID.randomUUID())
                    .avatarUrl(picture)
                    .googleId(googleId)
                    .emailVerified(true)
                    .role(Role.USER)
                    .banned(false)
                    .build();
            userRepository.save(user);
        }

        // Genera JWT + refresh token
        String jwt = jwtUtil.generateToken(user.getUsername());
        var refreshToken = refreshTokenService.createRefreshToken(user.getId());

        // Redirect al frontend con token nei query params
        String redirectUrl = frontendUrl + "/oauth2/callback"
                + "?token=" + jwt
                + "&refreshToken=" + refreshToken.getToken();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private String generateUsername(String name, String email) {
        if (name != null && !name.isBlank()) {
            return name.toLowerCase()
                    .replaceAll("[^a-z0-9]", "_")
                    .replaceAll("_+", "_")
                    .replaceAll("^_|_$", "");
        }
        return email.split("@")[0].replaceAll("[^a-z0-9_]", "_");
    }

    private String ensureUniqueUsername(String base) {
        String candidate = base.length() > 40 ? base.substring(0, 40) : base;
        if (userRepository.findByUsernameIgnoreCase(candidate).isEmpty()) {
            return candidate;
        }
        // Aggiunge numero random finché non è univoco
        int attempts = 0;
        while (attempts < 10) {
            String withSuffix = candidate + "_" + (int)(Math.random() * 9000 + 1000);
            if (userRepository.findByUsernameIgnoreCase(withSuffix).isEmpty()) {
                return withSuffix;
            }
            attempts++;
        }
        return candidate + "_" + UUID.randomUUID().toString().substring(0, 6);
    }
}
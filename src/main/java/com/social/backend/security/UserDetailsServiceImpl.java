package com.social.backend.security;

import com.social.backend.components.user.entity.User;
import com.social.backend.components.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Trim + case-insensitive: cerca l'utente ignorando maiuscole/minuscole e spazi
        String cleanUsername = username.trim();
        User user = userRepository.findByUsernameIgnoreCase(cleanUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Utente non trovato: " + cleanUsername));

        return new UserDetailsImpl(user);
    }
}
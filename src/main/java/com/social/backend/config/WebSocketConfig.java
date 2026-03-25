package com.social.backend.config;

import com.social.backend.security.JwtTokenProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    public WebSocketConfig(JwtTokenProvider jwtTokenProvider,
                           UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        "http://localhost:3000",
                        "http://localhost:5173",
                        "https://social-media-frontend-mu-neon.vercel.app"
                )
                .withSockJS();

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        "http://localhost:3000",
                        "http://localhost:5173",
                        "https://social-media-frontend-mu-neon.vercel.app/"
                );
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // Leggi il JWT dall'header Authorization del frame CONNECT
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        try {
                            if (jwtTokenProvider.validateToken(token)) {
                                String username = jwtTokenProvider.getUsernameFromToken(token);
                                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                                UsernamePasswordAuthenticationToken auth =
                                        new UsernamePasswordAuthenticationToken(
                                                userDetails, null, userDetails.getAuthorities());
                                // Setta il Principal nella sessione STOMP
                                accessor.setUser(auth);
                            }
                        } catch (Exception e) {
                            // Token non valido - connessione senza Principal
                        }
                    }
                }
                return message;
            }
        });
    }
}
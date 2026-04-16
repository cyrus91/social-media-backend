package com.social.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Rate limiter basato su Redis per gli endpoint di autenticazione.
 *
 * Strategia: contatore per (IP + path) con TTL fisso sulla finestra.
 * Se Redis non è disponibile, la richiesta viene lasciata passare (fail-open)
 * per non bloccare il servizio in caso di problemi infrastrutturali.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final StringRedisTemplate redisTemplate;

    // Configurazione per endpoint: path -> [max richieste, finestra in secondi]
    private static final Map<String, int[]> LIMITS = Map.of(
            "/api/auth/login",                new int[]{10,  60},   // 10 tentativi / minuto
            "/api/auth/register",             new int[]{5,   60},   // 5 registrazioni / minuto
            "/api/auth/forgot-password",      new int[]{5,  900},   // 5 richieste / 15 min
            "/api/auth/resend-verification",  new int[]{3,  900}    // 3 richieste / 15 min
    );

    public RateLimitInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        String path = request.getRequestURI();
        int[] limit = LIMITS.get(path);
        if (limit == null) return true;

        int maxRequests   = limit[0];
        int windowSeconds = limit[1];

        String ip  = extractClientIp(request);
        String key = "rate_limit:" + path + ":" + ip;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) return true; // Redis mock nei test → fail-open

            if (count == 1) {
                // Prima richiesta della finestra: imposta la scadenza
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }

            if (count > maxRequests) {
                logger.warn("Rate limit superato — IP: {}, path: {}, count: {}", ip, path, count);
                sendTooManyRequests(response, windowSeconds);
                return false;
            }

        } catch (Exception e) {
            // Redis non raggiungibile: fail-open per non bloccare il servizio
            logger.error("Rate limiter: errore Redis, richiesta lasciata passare — {}", e.getMessage());
        }

        return true;
    }

    /**
     * Estrae l'IP reale del client tenendo conto del reverse proxy di Koyeb.
     * Usa X-Forwarded-For se presente, altrimenti l'IP diretto.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void sendTooManyRequests(HttpServletResponse response, int retryAfterSeconds)
            throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter().write(
                "{\"error\":\"TOO_MANY_REQUESTS\"," +
                "\"message\":\"Troppi tentativi. Riprova più tardi.\"}"
        );
    }
}

package com.example.api.websocket;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class NotificationSocketTickets {
    public record Ticket(Integer userId, Instant tokenExpiresAt, Instant expiresAt) { }

    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public NotificationSocketTickets() { this(Clock.systemUTC()); }

    NotificationSocketTickets(Clock clock) { this.clock = clock; }

    // 浏览器的 WebSocket 不能加 Bearer 头，先用 JWT 换取一次性短票，URL 中不携带 JWT。
    public String issue(Integer userId, Instant tokenExpiresAt) {
        Instant now = this.clock.instant();
        if (userId == null || userId < 1 || tokenExpiresAt == null || !tokenExpiresAt.isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        byte[] bytes = new byte[32];
        this.random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        this.tickets.put(value, new Ticket(userId, tokenExpiresAt, now.plusSeconds(30)));
        return value;
    }

    public Optional<Ticket> consume(String value) {
        if (value == null || value.length() != 43) return Optional.empty();
        Ticket ticket = this.tickets.remove(value);
        Instant now = this.clock.instant();
        return ticket != null && ticket.expiresAt().isAfter(now) && ticket.tokenExpiresAt().isAfter(now)
            ? Optional.of(ticket) : Optional.empty();
    }

    @Scheduled(fixedDelay = 30000)
    public void removeExpired() {
        Instant now = this.clock.instant();
        this.tickets.values().removeIf(ticket -> !ticket.expiresAt().isAfter(now) || !ticket.tokenExpiresAt().isAfter(now));
    }
}

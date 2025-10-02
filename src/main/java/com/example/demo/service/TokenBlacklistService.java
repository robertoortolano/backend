package com.example.demo.service;

import com.example.demo.entity.InvalidatedToken;
import com.example.demo.repository.TokenBlacklistRepository;
import com.example.demo.security.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class TokenBlacklistService {

    private final TokenBlacklistRepository tokenBlacklistRepository;

    private final JwtTokenUtil jwtTokenUtil;

    // Aggiunge un token alla blacklist
    public void invalidateToken(String token) {
        if (!tokenBlacklistRepository.existsByToken(token)) {
            Instant expiration = jwtTokenUtil.extractExpiration(token).toInstant();
            InvalidatedToken invalidatedToken = new InvalidatedToken(token, expiration, null);
            tokenBlacklistRepository.save(invalidatedToken);
        }
    }

    // Verifica se un token è invalidato
    @Transactional(readOnly = true)
    public boolean isTokenInvalidated(String token) {
        return tokenBlacklistRepository.existsByToken(token);
    }

    // Pulizia automatica dei token scaduti (eseguito ogni giorno)
    @Scheduled(cron = "0 0 0 * * ?") // Mezzanotte ogni giorno
    public void cleanupExpiredTokens() {
        tokenBlacklistRepository.deleteAllExpiredSince(Instant.now());
    }
}
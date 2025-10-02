package com.example.demo.repository;

import com.example.demo.entity.InvalidatedToken;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface TokenBlacklistRepository extends JpaRepository<InvalidatedToken, String> {

    // Verifica se un token è nella blacklist
    boolean existsByToken(String token);

    // Elimina i token scaduti (può essere chiamato periodicamente)
    @Query("DELETE FROM InvalidatedToken t WHERE t.expirationTime < ?1")
    void deleteAllExpiredSince(Instant now);

    // Opzionale: trova un token per user (utile per logout globale)
    Optional<InvalidatedToken> findByUser(User user);
}
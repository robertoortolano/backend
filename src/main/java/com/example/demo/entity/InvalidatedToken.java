package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "invalidated_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvalidatedToken {

    public InvalidatedToken(String token) {
        this.token = token;
        this.expirationTime = Instant.now().plusSeconds(3600); // 1 ora di validità
        this.user = null; // Opzionale
    }

    @Id
    @Column(length = 512) // Lunghezza adatta per un JWT
    private String token;

    @Column(nullable = false)
    private Instant expirationTime; // Usato per pulire periodicamente i token scaduti

    // Opzionale: aggiungi user_id se vuoi tracciare chi ha fatto logout
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
}
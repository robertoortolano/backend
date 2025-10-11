package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "invalidated_tokens")
@Getter
@Setter
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvalidatedToken that = (InvalidatedToken) o;
        return token != null && Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
package com.example.demo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Email(message = "Username must be a valid email address")
    @Column(unique = true, nullable = false)
    private String username; // Email address - il sistema accetta solo email come username

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name")
    private String fullName;

    @Transient
    private Long activeTenant;

    // Relazione con Tenant ora gestita tramite UserRole
    // (rimossa relazione ManyToMany diretta)

    // Getter espliciti per evitare problemi con Lombok durante la compilazione
    public String getFullName() {
        return this.fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Long getActiveTenant() {
        return this.activeTenant;
    }

    public void setActiveTenant(Long activeTenant) {
        this.activeTenant = activeTenant;
    }

    // Alias per compatibilità con UserDetails
    public String getPasswordHash() {
        return this.password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id != null && Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        // Use a constant hash code for entities that are not yet persisted
        // This ensures consistency even before the ID is assigned
        return getClass().hashCode();
    }
}

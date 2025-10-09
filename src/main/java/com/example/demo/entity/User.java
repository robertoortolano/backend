package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name")
    private String fullName;

    @Transient
    private Long activeTenant;

    @ManyToMany
    @JoinTable(
            name = "user_tenant",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "tenant_id")
    )
    private Set<Tenant> tenants = new HashSet<>();

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
}

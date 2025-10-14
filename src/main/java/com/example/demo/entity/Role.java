package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rappresenta un ruolo custom creato dall'admin per essere assegnato alle Permission.
 * Esempi: "Developer", "QA", "Manager", "Viewer", etc.
 * 
 * NON confondere con UserRole che gestisce i ruoli di sistema (ADMIN/USER) 
 * per l'autenticazione e l'accesso a tenant/project.
 */
@Setter
@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private boolean defaultRole = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

}

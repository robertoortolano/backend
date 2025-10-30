package com.example.demo.entity;

import com.example.demo.enums.ScopeType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Rappresenta l'assegnazione di un ruolo (ADMIN o USER) ad un utente
 * in un contesto specifico (Tenant o Project).
 * 
 * Questo sistema è SEPARATO dalle Permission (ex-Grant) che gestiscono
 * i permessi granulari sugli item (Worker, Editor, Viewer, etc.)
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "user_role", uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_user_tenant_project_role_scope",
        columnNames = {"user_id", "tenant_id", "project_id", "role_name", "scope"}
    )
})
public class UserRole {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;  // NULL per ruoli a livello TENANT
    
    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;  // "ADMIN" o "USER"
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScopeType scope;  // TENANT o PROJECT
    
    /**
     * Verifica se questo è un ruolo ADMIN
     */
    public boolean isAdmin() {
        return "ADMIN".equals(roleName);
    }
    
    /**
     * Verifica se questo è un ruolo USER
     */
    public boolean isUser() {
        return "USER".equals(roleName);
    }
    
    /**
     * Verifica se questo è un ruolo a livello TENANT
     */
    public boolean isTenantLevel() {
        return scope == ScopeType.TENANT && project == null;
    }
    
    /**
     * Verifica se questo è un ruolo a livello PROJECT
     */
    public boolean isProjectLevel() {
        return scope == ScopeType.PROJECT && project != null;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserRole)) return false;
        UserRole userRole = (UserRole) o;
        return id != null && id.equals(userRole.id);
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}






























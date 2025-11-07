package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Assegnazione globale di ruoli e grant a una Permission.
 * Rappresenta l'associazione tra una Permission specifica e i suoi ruoli custom + grant.
 * 
 * On-demand: viene creato solo quando necessario (quando si assegna un ruolo o grant).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "permission_assignment",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"permission_type", "permission_id", "tenant_id"}
       ))
public class PermissionAssignment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Tipo della Permission (polimorfico).
     * Esempi: "FieldOwnerPermission", "ExecutorPermission", "StatusOwnerPermission", ecc.
     */
    @Column(name = "permission_type", nullable = false, length = 100)
    private String permissionType;
    
    /**
     * ID della Permission specifica.
     */
    @Column(name = "permission_id", nullable = false)
    private Long permissionId;
    
    /**
     * Ruoli custom assegnati a questa Permission.
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "permission_assignment_role",
        joinColumns = @JoinColumn(name = "permission_assignment_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
    
    /**
     * Grant diretto per questa Permission (on-demand, può essere null).
     */
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "grant_id")
    private Grant grant;
    
    /**
     * Tenant di appartenenza.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
}


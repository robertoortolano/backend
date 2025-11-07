package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Assegnazione di ruoli e grant a una Permission.
 * Può essere globale (project = null) o specifica per un progetto (project != null).
 * 
 * Rappresenta l'associazione tra una Permission specifica e i suoi ruoli custom + grant.
 * 
 * On-demand: viene creato solo quando necessario (quando si assegna un ruolo o grant).
 * 
 * IMPORTANTE: Il vincolo unico su (permission_type, permission_id, tenant_id, project_id) permette:
 * - Una assegnazione globale: (permissionType, permissionId, tenant, null)
 * - Una assegnazione per progetto: (permissionType, permissionId, tenant, projectId)
 * 
 * Questo permette assegnazioni completamente indipendenti tra globale e progetto.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "permission_assignment",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"permission_type", "permission_id", "tenant_id", "project_id"}
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
    
    /**
     * Progetto a cui si applica questa assegnazione.
     * Se NULL, è un'assegnazione globale.
     * Se presente, è un'assegnazione specifica per quel progetto.
     * 
     * Permette di avere assegnazioni completamente indipendenti:
     * - Assegnazione globale: (permissionType, permissionId, tenant, null)
     * - Assegnazione di progetto: (permissionType, permissionId, tenant, projectId)
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "project_id", nullable = true)
    private Project project;
    
    /**
     * ItemTypeSet di riferimento (per identificare l'ITS quando è un'assegnazione di progetto).
     * Utile per query e per mantenere il contesto dell'ITS.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "itemtypeset_id", nullable = true)
    private ItemTypeSet itemTypeSet;
    
}


package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Assegnazione di progetto per una Permission.
 * Rappresenta l'override a livello di progetto per ruoli e grant di una Permission.
 * 
 * Utilizzato per:
 * - ITS globali: override di PermissionAssignment globale per un progetto specifico
 * - ITS di progetto: le Permission sono già di progetto, ma possono avere override per progetti specifici
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "project_permission_assignment",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"permission_type", "permission_id", "project_id", "tenant_id"}
       ))
public class ProjectPermissionAssignment {
    
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
     * PermissionAssignment di progetto (contiene ruoli + grant specifici per il progetto).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "permission_assignment_id", nullable = false)
    private PermissionAssignment assignment;
    
    /**
     * Progetto a cui si applica questa assegnazione.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    
    /**
     * ItemTypeSet di riferimento (per identificare l'ITS).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itemtypeset_id", nullable = false)
    private ItemTypeSet itemTypeSet;
    
    /**
     * Tenant di appartenenza.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
}


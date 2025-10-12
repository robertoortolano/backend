package com.example.demo.entity;

import com.example.demo.enums.ItemTypeSetRoleType;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "itemtypeset_role")
public class ItemTypeSetRole {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemTypeSetRoleType roleType;
    
    @Column(nullable = false)
    private String name;
    
    private String description;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itemtypeset_id", nullable = false)
    private ItemTypeSet itemTypeSet;
    
    // Riferimento all'entità specifica per cui è stato creato il ruolo
    @Column(name = "related_entity_type")
    private String relatedEntityType; // "ItemType", "WorkflowStatus", "FieldConfiguration", "Workflow", "Transition", "ItemTypeConfiguration"
    
    @Column(name = "related_entity_id")
    private Long relatedEntityId;
    
    // Per i ruoli EDITOR e VIEWER che sono legati alle coppie (FieldConfiguration, WorkflowStatus)
    @Column(name = "secondary_entity_type")
    private String secondaryEntityType; // "FieldConfiguration" o "WorkflowStatus" per le coppie
    
    @Column(name = "secondary_entity_id")
    private Long secondaryEntityId;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
    // Assegnazione DUAL: Grant diretto OPPURE Role template
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_id")
    private Grant grant;           // Assegnazione diretta Grant
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_template_id")
    private Role roleTemplate;     // Assegnazione via Role template
    
    @Builder.Default
    @OneToMany(mappedBy = "itemTypeSetRole", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ItemTypeSetRoleGrant> grants = new HashSet<>();
    
    // Metodi di utilità per identificare l'entità correlata
    public boolean isForItemType() {
        return "ItemType".equals(relatedEntityType);
    }
    
    public boolean isForWorkflowStatus() {
        return "WorkflowStatus".equals(relatedEntityType);
    }
    
    public boolean isForFieldConfiguration() {
        return "FieldConfiguration".equals(relatedEntityType) || 
               (ItemTypeSetRoleType.FIELD_OWNERS.equals(roleType) && "FieldConfiguration".equals(relatedEntityType));
    }
    
    public boolean isForWorkflow() {
        return "Workflow".equals(relatedEntityType);
    }
    
    public boolean isForTransition() {
        return "Transition".equals(relatedEntityType);
    }
    
    public boolean isForItemTypeConfiguration() {
        return "ItemTypeConfiguration".equals(relatedEntityType);
    }
    
    public boolean isForFieldConfigurationWorkflowStatusPair() {
        return (ItemTypeSetRoleType.EDITORS.equals(roleType) || ItemTypeSetRoleType.VIEWERS.equals(roleType))
               && "ItemTypeConfiguration".equals(relatedEntityType);
    }
    
    // Metodi di utilità per gestire l'assegnazione dual
    public boolean hasDirectGrant() {
        return grant != null;
    }
    
    public boolean hasRoleTemplate() {
        return roleTemplate != null;
    }
    
    public boolean hasAnyAssignment() {
        return hasDirectGrant() || hasRoleTemplate() || (grants != null && !grants.isEmpty());
    }
    
    public String getAssignmentType() {
        if (hasDirectGrant()) {
            return "GRANT";
        } else if (hasRoleTemplate()) {
            return "ROLE";
        } else if (grants != null && !grants.isEmpty()) {
            return "GRANTS";
        }
        return "NONE";
    }
}

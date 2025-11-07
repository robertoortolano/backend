package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FieldStatusPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itemtypeconfiguration_id", nullable = false)
    private ItemTypeConfiguration itemTypeConfiguration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id", nullable = false)
    private Field field;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflowstatus_id", nullable = false)
    private WorkflowStatus workflowStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PermissionType permissionType; // EDITORS o VIEWERS

    // Nota: assignedRoles e grant sono ora gestiti tramite PermissionAssignment
    // Usa PermissionAssignmentService per recuperare/gestire ruoli e grant

    public enum PermissionType {
        EDITORS,
        VIEWERS
    }
}


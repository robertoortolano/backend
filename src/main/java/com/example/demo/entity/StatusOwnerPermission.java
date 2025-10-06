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
public class StatusOwnerPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itemtypeconfiguration_id", nullable = false)
    private ItemTypeConfiguration itemTypeConfiguration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflowstatus_id", nullable = false)
    private WorkflowStatus workflowStatus;

    // Relazioni con ruoli personalizzati
    @ManyToMany
    @JoinTable(
            name = "statusownerpermission_role",
            joinColumns = @JoinColumn(name = "statusownerpermission_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> assignedRoles = new HashSet<>();

    // Relazioni con grants (utenti/gruppi)
    @ManyToMany
    @JoinTable(
            name = "statusownerpermission_grant",
            joinColumns = @JoinColumn(name = "statusownerpermission_id"),
            inverseJoinColumns = @JoinColumn(name = "grant_id")
    )
    private Set<Grant> assignedGrants = new HashSet<>();
}


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
public class WorkerPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itemtypeconfiguration_id", nullable = false)
    private ItemTypeConfiguration itemTypeConfiguration;

    // Relazioni con ruoli personalizzati
    @ManyToMany
    @JoinTable(
            name = "workerpermission_role",
            joinColumns = @JoinColumn(name = "workerpermission_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> assignedRoles = new HashSet<>();

    // Relazioni con grants (utenti/gruppi)
    @ManyToMany
    @JoinTable(
            name = "workerpermission_grant",
            joinColumns = @JoinColumn(name = "workerpermission_id"),
            inverseJoinColumns = @JoinColumn(name = "grant_id")
    )
    private Set<Grant> assignedGrants = new HashSet<>();
}


package com.example.demo.entity;

import com.example.demo.enums.ScopeType;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
public class ItemTypeSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScopeType scope;

    @Builder.Default
    private boolean defaultItemTypeSet = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Builder.Default
    @OneToMany(mappedBy = "itemTypeSet")
    private Set<Project> projectsAssociation = new HashSet<>();

    @Builder.Default
    @OneToMany
    private Set<ItemTypeConfiguration> itemTypeConfigurations = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Builder.Default
    @OneToMany(mappedBy = "itemTypeSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ItemTypeSetRole> roles = new HashSet<>();

}

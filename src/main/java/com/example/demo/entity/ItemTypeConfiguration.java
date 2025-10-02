package com.example.demo.entity;

import com.example.demo.enums.ItemTypeCategory;
import com.example.demo.enums.ScopeType;
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
public class ItemTypeConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemTypeCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScopeType scope;

    private boolean defaultItemTypeConfiguration = false;

    @ManyToOne
    private ItemType itemType;

    @ManyToMany
    @JoinTable(
            name = "itemtypeconfiguration_role",
            joinColumns = @JoinColumn(name = "itemtypeconfiguration_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> workers = new HashSet<>();

    @ManyToOne
    private Workflow workflow;

    @ManyToOne
    private FieldSet fieldSet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;
}


package com.example.demo.entity;

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
@Table(name = "itemtypeset_role_grant")
public class ItemTypeSetRoleGrant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itemtypeset_role_id", nullable = false)
    private ItemTypeSetRole itemTypeSetRole;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grant_id", nullable = false)
    private Grant grant;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
    // Metodi di utilità per accedere alle informazioni del grant
    public Set<User> getGrantedUsers() {
        return grant != null ? grant.getUsers() : new HashSet<>();
    }
    
    public Set<Group> getGrantedGroups() {
        return grant != null ? grant.getGroups() : new HashSet<>();
    }
    
    public Set<User> getNegatedUsers() {
        return grant != null ? grant.getNegatedUsers() : new HashSet<>();
    }
    
    public Set<Group> getNegatedGroups() {
        return grant != null ? grant.getNegatedGroups() : new HashSet<>();
    }
}


package com.example.demo.dto;

import lombok.*;

import java.util.Set;

/**
 * DTO per PermissionAssignment (assegnazione di ruoli e grant a Permission).
 * Può essere globale (projectId = null) o specifica per un progetto (projectId != null).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionAssignmentDto {
    
    private Long id;
    
    private String permissionType;
    
    private Long permissionId;
    
    private Set<Long> roleIds;
    
    private Set<RoleViewDto> roles;
    
    private Long grantId;
    
    private GrantViewDto grant;
    
    private Long tenantId;
    
    /**
     * ID del progetto a cui si applica questa assegnazione.
     * Se null, è un'assegnazione globale.
     */
    private Long projectId;
    
    /**
     * ID dell'ItemTypeSet di riferimento (utile per assegnazioni di progetto).
     */
    private Long itemTypeSetId;
    
}


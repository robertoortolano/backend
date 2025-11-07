package com.example.demo.dto;

import lombok.*;

import java.util.Set;

/**
 * DTO per PermissionAssignment (assegnazione globale di ruoli e grant a Permission).
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
    
}


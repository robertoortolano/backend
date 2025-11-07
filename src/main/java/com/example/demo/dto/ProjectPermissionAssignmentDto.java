package com.example.demo.dto;

import lombok.*;

/**
 * DTO per ProjectPermissionAssignment (assegnazione di progetto per Permission).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectPermissionAssignmentDto {
    
    private Long id;
    
    private String permissionType;
    
    private Long permissionId;
    
    private Long projectId;
    
    private Long itemTypeSetId;
    
    private PermissionAssignmentDto assignment;
    
    private Long tenantId;
    
}


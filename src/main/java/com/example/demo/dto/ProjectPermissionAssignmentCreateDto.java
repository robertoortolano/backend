package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Set;

/**
 * DTO per creare o aggiornare ProjectPermissionAssignment.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectPermissionAssignmentCreateDto {
    
    @NotNull(message = "Permission type is required")
    private String permissionType;
    
    @NotNull(message = "Permission ID is required")
    private Long permissionId;
    
    @NotNull(message = "Project ID is required")
    private Long projectId;
    
    @NotNull(message = "ItemTypeSet ID is required")
    private Long itemTypeSetId;
    
    private Set<Long> roleIds;
    
    private Long grantId;
    
}


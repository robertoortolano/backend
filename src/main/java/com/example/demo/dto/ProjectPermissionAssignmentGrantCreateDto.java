package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Set;

/**
 * DTO per creare un Grant e assegnarlo a ProjectPermissionAssignment.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectPermissionAssignmentGrantCreateDto {
    
    @NotNull(message = "Permission type is required")
    private String permissionType;
    
    @NotNull(message = "Permission ID is required")
    private Long permissionId;
    
    @NotNull(message = "Project ID is required")
    private Long projectId;
    
    @NotNull(message = "ItemTypeSet ID is required")
    private Long itemTypeSetId;
    
    private Set<Long> userIds;
    
    private Set<Long> groupIds;
    
    private Set<Long> negatedUserIds;
    
    private Set<Long> negatedGroupIds;
    
}


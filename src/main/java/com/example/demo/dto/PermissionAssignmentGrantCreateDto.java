package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Set;

/**
 * DTO per creare un Grant e assegnarlo a PermissionAssignment.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionAssignmentGrantCreateDto {
    
    @NotNull(message = "Permission type is required")
    private String permissionType;
    
    @NotNull(message = "Permission ID is required")
    private Long permissionId;
    
    private Set<Long> userIds;
    
    private Set<Long> groupIds;
    
    private Set<Long> negatedUserIds;
    
    private Set<Long> negatedGroupIds;
    
}


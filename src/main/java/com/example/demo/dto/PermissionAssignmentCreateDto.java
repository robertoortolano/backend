package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Set;

/**
 * DTO per creare o aggiornare PermissionAssignment.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionAssignmentCreateDto {
    
    @NotNull(message = "Permission type is required")
    private String permissionType;
    
    @NotNull(message = "Permission ID is required")
    private Long permissionId;
    
    private Set<Long> roleIds;
    
    private Long grantId;
    
}


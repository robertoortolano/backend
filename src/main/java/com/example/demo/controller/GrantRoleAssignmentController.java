package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.GrantProjectRoleCreateDto;
import com.example.demo.entity.GrantRoleAssignment;
import com.example.demo.entity.Tenant;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.GrantRoleAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/grant-role-assignments")
@RequiredArgsConstructor
public class GrantRoleAssignmentController {

    private final GrantRoleAssignmentService grantRoleAssignmentService;

    /**
     * Crea un GrantRoleAssignment PROJECT-level
     * Collega un Grant (con users/groups) a un Role template in un progetto specifico
     */
    @PostMapping("/project")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#dto.projectId, 'ADMIN')")
    public ResponseEntity<ApiResponse> createProjectGrantRoleAssignment(
            @Valid @RequestBody GrantProjectRoleCreateDto dto,
            @CurrentTenant Tenant tenant,
            Principal principal
    ) {
        grantRoleAssignmentService.createProjectGrantRoleAssignment(
                dto.grantId(),
                dto.roleId(),
                dto.projectId(),
                tenant
        );
        
        return ResponseEntity.ok(new ApiResponse(
                "GrantRoleAssignment created successfully for project", 200));
    }

    /**
     * Elimina un GrantRoleAssignment PROJECT-level
     */
    @DeleteMapping("/project/{projectId}/grant/{grantId}/role/{roleId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'ADMIN')")
    public ResponseEntity<ApiResponse> deleteProjectGrantRoleAssignment(
            @PathVariable Long projectId,
            @PathVariable Long grantId,
            @PathVariable Long roleId,
            @CurrentTenant Tenant tenant,
            Principal principal
    ) {
        grantRoleAssignmentService.deleteProjectGrantRoleAssignment(
                grantId, roleId, projectId, tenant);
        
        return ResponseEntity.ok(new ApiResponse(
                "GrantRoleAssignment deleted successfully", 200));
    }

    /**
     * Ottiene tutti i GrantRoleAssignment per un progetto
     */
    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'ADMIN')")
    public ResponseEntity<List<GrantRoleAssignment>> getProjectGrantRoleAssignments(
            @PathVariable Long projectId,
            @CurrentTenant Tenant tenant,
            Principal principal
    ) {
        List<GrantRoleAssignment> assignments = grantRoleAssignmentService.getProjectGrantRoleAssignments(
                projectId, tenant);
        
        return ResponseEntity.ok(assignments);
    }

    /**
     * Ottiene tutti i GrantRoleAssignment per un ruolo in un progetto
     */
    @GetMapping("/project/{projectId}/role/{roleId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'ADMIN')")
    public ResponseEntity<List<GrantRoleAssignment>> getProjectGrantRoleAssignmentsByRole(
            @PathVariable Long projectId,
            @PathVariable Long roleId,
            @CurrentTenant Tenant tenant,
            Principal principal
    ) {
        List<GrantRoleAssignment> assignments = grantRoleAssignmentService.getProjectGrantRoleAssignmentsByRole(
                projectId, roleId, tenant);
        
        return ResponseEntity.ok(assignments);
    }
}


package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.PermissionAssignment;
import com.example.demo.entity.Tenant;
import com.example.demo.mapper.PermissionAssignmentMapper;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.ProjectPermissionAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Controller per gestire PermissionAssignment di progetto (assegnazioni specifiche per progetto).
 * 
 * NOTA: Questo controller gestisce PermissionAssignment con project != null.
 * Le assegnazioni sono completamente indipendenti da quelle globali (project = null).
 */
@RestController
@RequestMapping("/api/project-permission-assignments")
@RequiredArgsConstructor
public class ProjectPermissionAssignmentController {
    
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;
    private final PermissionAssignmentMapper permissionAssignmentMapper;
    
    /**
     * Crea o aggiorna un PermissionAssignment per una Permission e un progetto.
     */
    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#dto.projectId, 'PROJECT_ADMIN')")
    public ResponseEntity<PermissionAssignmentDto> createOrUpdateProjectAssignment(
            @Valid @RequestBody ProjectPermissionAssignmentCreateDto dto,
            @CurrentTenant Tenant tenant) {
        PermissionAssignment assignment = projectPermissionAssignmentService.createOrUpdateProjectAssignment(
                dto.getPermissionType(),
                dto.getPermissionId(),
                dto.getProjectId(),
                dto.getItemTypeSetId(),
                dto.getRoleIds(),
                dto.getGrantId(),
                tenant);
        return ResponseEntity.ok(permissionAssignmentMapper.toDto(assignment));
    }
    
    /**
     * Ottiene PermissionAssignment per una Permission e un progetto.
     * Restituisce un oggetto vuoto (con grant null e roles vuoti) se non esiste, invece di 404,
     * per evitare errori in console quando le grant vengono create on-demand.
     */
    @GetMapping("/{permissionType}/{permissionId}/project/{projectId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'PROJECT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'PROJECT_USER')")
    public ResponseEntity<PermissionAssignmentDto> getProjectAssignment(
            @PathVariable String permissionType,
            @PathVariable Long permissionId,
            @PathVariable Long projectId,
            @CurrentTenant Tenant tenant) {
        Optional<PermissionAssignment> assignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                permissionType, permissionId, projectId, tenant);
        if (assignmentOpt.isPresent()) {
            return ResponseEntity.ok(permissionAssignmentMapper.toDto(assignmentOpt.get()));
        } else {
            // Restituisce un oggetto vuoto invece di 404 per evitare errori in console
            // quando le grant vengono create on-demand
            PermissionAssignmentDto emptyDto = PermissionAssignmentDto.builder()
                    .permissionType(permissionType)
                    .permissionId(permissionId)
                    .projectId(projectId)
                    .tenantId(tenant.getId())
                    .grantId(null)
                    .grant(null)
                    .roles(null)
                    .roleIds(null)
                    .build();
            return ResponseEntity.ok(emptyDto);
        }
    }
    
    /**
     * Elimina PermissionAssignment per una Permission e un progetto.
     */
    @DeleteMapping("/{permissionType}/{permissionId}/project/{projectId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'PROJECT_ADMIN')")
    public ResponseEntity<Void> deleteProjectAssignment(
            @PathVariable String permissionType,
            @PathVariable Long permissionId,
            @PathVariable Long projectId,
            @CurrentTenant Tenant tenant) {
        projectPermissionAssignmentService.deleteProjectAssignment(
                permissionType, permissionId, projectId, tenant);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Crea un Grant e lo assegna a PermissionAssignment di progetto.
     */
    @PostMapping("/create-and-assign-grant")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#dto.projectId, 'PROJECT_ADMIN')")
    public ResponseEntity<PermissionAssignmentDto> createAndAssignGrant(
            @Valid @RequestBody ProjectPermissionAssignmentGrantCreateDto dto,
            @CurrentTenant Tenant tenant) {
        PermissionAssignment assignment = projectPermissionAssignmentService.createAndAssignGrant(
                dto.getPermissionType(),
                dto.getPermissionId(),
                dto.getProjectId(),
                dto.getItemTypeSetId(),
                dto.getUserIds(),
                dto.getGroupIds(),
                dto.getNegatedUserIds(),
                dto.getNegatedGroupIds(),
                tenant);
        return ResponseEntity.ok(permissionAssignmentMapper.toDto(assignment));
    }
    
}


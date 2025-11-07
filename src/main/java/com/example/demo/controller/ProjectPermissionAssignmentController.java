package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.ProjectPermissionAssignment;
import com.example.demo.entity.Tenant;
import com.example.demo.mapper.ProjectPermissionAssignmentMapper;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.ProjectPermissionAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Controller per gestire ProjectPermissionAssignment (assegnazioni di progetto per Permission).
 */
@RestController
@RequestMapping("/api/project-permission-assignments")
@RequiredArgsConstructor
public class ProjectPermissionAssignmentController {
    
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;
    private final ProjectPermissionAssignmentMapper projectPermissionAssignmentMapper;
    
    /**
     * Crea o aggiorna un ProjectPermissionAssignment per una Permission e un progetto.
     */
    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#dto.projectId, 'PROJECT_ADMIN')")
    public ResponseEntity<ProjectPermissionAssignmentDto> createOrUpdateProjectAssignment(
            @Valid @RequestBody ProjectPermissionAssignmentCreateDto dto,
            @CurrentTenant Tenant tenant) {
        ProjectPermissionAssignment projectAssignment = projectPermissionAssignmentService.createOrUpdateProjectAssignment(
                dto.getPermissionType(),
                dto.getPermissionId(),
                dto.getProjectId(),
                dto.getItemTypeSetId(),
                dto.getRoleIds(),
                dto.getGrantId(),
                tenant);
        return ResponseEntity.ok(projectPermissionAssignmentMapper.toDto(projectAssignment));
    }
    
    /**
     * Ottiene ProjectPermissionAssignment per una Permission e un progetto.
     * Restituisce un oggetto vuoto (con assignment null) se non esiste, invece di 404,
     * per evitare errori in console quando le grant vengono create on-demand.
     */
    @GetMapping("/{permissionType}/{permissionId}/project/{projectId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'PROJECT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'PROJECT_USER')")
    public ResponseEntity<ProjectPermissionAssignmentDto> getProjectAssignment(
            @PathVariable String permissionType,
            @PathVariable Long permissionId,
            @PathVariable Long projectId,
            @CurrentTenant Tenant tenant) {
        Optional<ProjectPermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                permissionType, permissionId, projectId, tenant);
        if (projectAssignmentOpt.isPresent()) {
            return ResponseEntity.ok(projectPermissionAssignmentMapper.toDto(projectAssignmentOpt.get()));
        } else {
            // Restituisce un oggetto vuoto invece di 404 per evitare errori in console
            // quando le grant vengono create on-demand
            ProjectPermissionAssignmentDto emptyDto = ProjectPermissionAssignmentDto.builder()
                    .permissionType(permissionType)
                    .permissionId(permissionId)
                    .projectId(projectId)
                    .tenantId(tenant.getId())
                    .assignment(null) // Nessun assignment esistente
                    .build();
            return ResponseEntity.ok(emptyDto);
        }
    }
    
    /**
     * Elimina ProjectPermissionAssignment per una Permission e un progetto.
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
     * Crea un Grant e lo assegna a ProjectPermissionAssignment.
     */
    @PostMapping("/create-and-assign-grant")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#dto.projectId, 'PROJECT_ADMIN')")
    public ResponseEntity<ProjectPermissionAssignmentDto> createAndAssignGrant(
            @Valid @RequestBody ProjectPermissionAssignmentGrantCreateDto dto,
            @CurrentTenant Tenant tenant) {
        ProjectPermissionAssignment projectAssignment = projectPermissionAssignmentService.createAndAssignGrant(
                dto.getPermissionType(),
                dto.getPermissionId(),
                dto.getProjectId(),
                dto.getItemTypeSetId(),
                dto.getUserIds(),
                dto.getGroupIds(),
                dto.getNegatedUserIds(),
                dto.getNegatedGroupIds(),
                tenant);
        return ResponseEntity.ok(projectPermissionAssignmentMapper.toDto(projectAssignment));
    }
    
}


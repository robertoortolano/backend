package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.PermissionAssignment;
import com.example.demo.entity.Tenant;
import com.example.demo.mapper.PermissionAssignmentMapper;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.PermissionAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Controller per gestire PermissionAssignment (assegnazioni globali di ruoli e grant a Permission).
 */
@RestController
@RequestMapping("/api/permission-assignments")
@RequiredArgsConstructor
public class PermissionAssignmentController {
    
    private final PermissionAssignmentService permissionAssignmentService;
    private final PermissionAssignmentMapper permissionAssignmentMapper;
    
    /**
     * Crea o aggiorna un PermissionAssignment per una Permission.
     */
    @PostMapping
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<PermissionAssignmentDto> createOrUpdateAssignment(
            @Valid @RequestBody PermissionAssignmentCreateDto dto,
            @CurrentTenant Tenant tenant) {
        PermissionAssignment assignment = permissionAssignmentService.createOrUpdateAssignment(
                dto.getPermissionType(),
                dto.getPermissionId(),
                dto.getRoleIds(),
                dto.getGrantId(),
                tenant);
        return ResponseEntity.ok(permissionAssignmentMapper.toDto(assignment));
    }
    
    /**
     * Ottiene PermissionAssignment per una Permission.
     */
    @GetMapping("/{permissionType}/{permissionId}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<PermissionAssignmentDto> getAssignment(
            @PathVariable String permissionType,
            @PathVariable Long permissionId,
            @CurrentTenant Tenant tenant) {
        Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                permissionType, permissionId, tenant);
        if (assignmentOpt.isPresent()) {
            return ResponseEntity.ok(permissionAssignmentMapper.toDto(assignmentOpt.get()));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Elimina PermissionAssignment per una Permission.
     */
    @DeleteMapping("/{permissionType}/{permissionId}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable String permissionType,
            @PathVariable Long permissionId,
            @CurrentTenant Tenant tenant) {
        permissionAssignmentService.deleteAssignment(permissionType, permissionId, tenant);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Aggiunge un ruolo a PermissionAssignment esistente.
     */
    @PostMapping("/{permissionType}/{permissionId}/roles/{roleId}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<PermissionAssignmentDto> addRole(
            @PathVariable String permissionType,
            @PathVariable Long permissionId,
            @PathVariable Long roleId,
            @CurrentTenant Tenant tenant) {
        PermissionAssignment assignment = permissionAssignmentService.addRole(
                permissionType, permissionId, roleId, tenant);
        return ResponseEntity.ok(permissionAssignmentMapper.toDto(assignment));
    }
    
    /**
     * Rimuove un ruolo da PermissionAssignment esistente.
     */
    @DeleteMapping("/{permissionType}/{permissionId}/roles/{roleId}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<PermissionAssignmentDto> removeRole(
            @PathVariable String permissionType,
            @PathVariable Long permissionId,
            @PathVariable Long roleId,
            @CurrentTenant Tenant tenant) {
        PermissionAssignment assignment = permissionAssignmentService.removeRole(
                permissionType, permissionId, roleId, tenant);
        if (assignment != null) {
            return ResponseEntity.ok(permissionAssignmentMapper.toDto(assignment));
        } else {
            return ResponseEntity.noContent().build();
        }
    }
    
    /**
     * Crea un Grant e lo assegna a PermissionAssignment.
     */
    @PostMapping("/create-and-assign-grant")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<PermissionAssignmentDto> createAndAssignGrant(
            @Valid @RequestBody PermissionAssignmentGrantCreateDto dto,
            @CurrentTenant Tenant tenant) {
        PermissionAssignment assignment = permissionAssignmentService.createAndAssignGrant(
                dto.getPermissionType(),
                dto.getPermissionId(),
                dto.getUserIds(),
                dto.getGroupIds(),
                dto.getNegatedUserIds(),
                dto.getNegatedGroupIds(),
                tenant);
        return ResponseEntity.ok(permissionAssignmentMapper.toDto(assignment));
    }
    
}


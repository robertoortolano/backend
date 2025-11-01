package com.example.demo.controller;

import com.example.demo.dto.GrantDetailsDto;
import com.example.demo.dto.ItemTypeSetRoleGrantCreateDto;
import com.example.demo.entity.Tenant;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.ProjectItemTypeSetRoleGrantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/project-itemtypeset-role-grants")
@RequiredArgsConstructor
public class ProjectItemTypeSetRoleGrantController {
    
    private final ProjectItemTypeSetRoleGrantService projectItemTypeSetRoleGrantService;
    
    /**
     * Crea o aggiorna un Grant di progetto per un ItemTypeSetRole
     */
    @PostMapping("/project/{projectId}")
    @PreAuthorize("@projectSecurityService.hasProjectRole(principal, #tenant, #projectId, 'PROJECT_ADMIN')")
    public ResponseEntity<?> createOrUpdateProjectGrant(
            @PathVariable Long projectId,
            @Valid @RequestBody ItemTypeSetRoleGrantCreateDto dto,
            @CurrentTenant Tenant tenant) {
        try {
            projectItemTypeSetRoleGrantService.createOrUpdateProjectGrant(dto, projectId, tenant);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }
    
    /**
     * Recupera i dettagli di un Grant di progetto per un ItemTypeSetRole
     */
    @GetMapping("/project/{projectId}/role/{roleId}")
    @PreAuthorize("@projectSecurityService.hasProjectRole(principal, #tenant, #projectId, 'PROJECT_ADMIN') or @projectSecurityService.hasProjectRole(principal, #tenant, #projectId, 'PROJECT_USER')")
    public ResponseEntity<GrantDetailsDto> getProjectGrantDetails(
            @PathVariable Long projectId,
            @PathVariable Long roleId,
            @CurrentTenant Tenant tenant) {
        try {
            GrantDetailsDto details = projectItemTypeSetRoleGrantService.getProjectGrantDetails(roleId, projectId, tenant);
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    /**
     * Rimuove un Grant di progetto per un ItemTypeSetRole
     */
    @DeleteMapping("/project/{projectId}/role/{roleId}")
    @PreAuthorize("@projectSecurityService.hasProjectRole(principal, #tenant, #projectId, 'PROJECT_ADMIN')")
    public ResponseEntity<?> removeProjectGrant(
            @PathVariable Long projectId,
            @PathVariable Long roleId,
            @CurrentTenant Tenant tenant) {
        try {
            projectItemTypeSetRoleGrantService.removeProjectGrant(roleId, projectId, tenant);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }
    
    /**
     * Verifica se esiste un Grant di progetto per un ItemTypeSetRole
     */
    @GetMapping("/project/{projectId}/role/{roleId}/exists")
    @PreAuthorize("@projectSecurityService.hasProjectRole(principal, #tenant, #projectId, 'PROJECT_ADMIN') or @projectSecurityService.hasProjectRole(principal, #tenant, #projectId, 'PROJECT_USER')")
    public ResponseEntity<Boolean> hasProjectGrant(
            @PathVariable Long projectId,
            @PathVariable Long roleId,
            @CurrentTenant Tenant tenant) {
        boolean exists = projectItemTypeSetRoleGrantService.hasProjectGrant(roleId, projectId, tenant.getId());
        return ResponseEntity.ok(exists);
    }
}

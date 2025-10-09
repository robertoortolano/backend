package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.security.CurrentUser;
import com.example.demo.service.ItemTypeSetService;
import com.example.demo.service.ProjectService;
import com.example.demo.security.CurrentTenant;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projectsAssociation")
@RequiredArgsConstructor
public class ProjectController {

    private final  ProjectService projectService;
    private final ItemTypeSetService itemTypeSetService;


    @PostMapping
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<ProjectViewDto> createProject(
            @CurrentTenant Tenant tenant,
            @CurrentUser User user,
            @Valid @RequestBody ProjectCreateDto projectCreateDTO) {

            return ResponseEntity.ok(projectService.createProjectForCurrentUser(tenant, user, projectCreateDTO));
    }


    @GetMapping("/by-user")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProjectViewDto>> getProjectsForCurrentUser(
            @CurrentTenant Tenant tenant,
            @CurrentUser User user) {

        return ResponseEntity.ok(projectService.getProjectsForUser(tenant, user));
    }



    @GetMapping("/{projectId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasAnyProjectRoleInList(#projectId, authentication.name, {'PROJECT_ADMIN', 'USER'})")
    public ResponseEntity<ProjectViewDto> getProjectById(
            @PathVariable Long projectId,
            @CurrentTenant Tenant tenant
    ) {
            return ResponseEntity.ok(projectService.getById(tenant, projectId));
    }


    @PutMapping("/{projectId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'PROJECT_ADMIN')")
    public ResponseEntity<ProjectViewDto> updateProject(
            @PathVariable Long projectId,
            @RequestBody ProjectUpdateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        return ResponseEntity.ok(projectService.updateProject(tenant, projectId, dto));
    }


    @PutMapping("/{projectId}/item-type-set/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'PROJECT_ADMIN')")
    public ResponseEntity<ItemTypeSetViewDto> update(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @RequestBody ItemTypeSetUpdateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        ItemTypeSetViewDto updated = itemTypeSetService.updateItemTypeSet(tenant, id, dto);
        return ResponseEntity.ok(updated);
    }

/*

    @PatchMapping("/{projectId}/item-type-set/{setId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'PROJECT_ADMIN')")
    public ResponseEntity<?> assignItemTypeSet(
            @PathVariable Long projectId,
            @PathVariable Long setId,
            @CurrentUser User user) {

        projectService.assignItemTypeSet(projectId, setId, user);
        return ResponseEntity.ok().build();
    }

 */


}

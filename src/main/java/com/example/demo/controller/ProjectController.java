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
@RequestMapping("/api/projects")
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
            @CurrentTenant Tenant tenant,
            @CurrentUser User user
    ) {
        return ResponseEntity.ok(projectService.updateProject(tenant, projectId, dto, user));
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

    @PostMapping("/{projectId}/users/{userId}/assign-admin")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse> assignProjectAdmin(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @CurrentTenant Tenant tenant,
            @CurrentUser User currentUser
    ) {
        projectService.assignProjectAdmin(projectId, userId, tenant);
        return ResponseEntity.ok(new ApiResponse("Project admin assigned successfully", 200));
    }

    @DeleteMapping("/{projectId}/users/{userId}/revoke-admin")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse> revokeProjectAdmin(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @CurrentTenant Tenant tenant
    ) {
        projectService.revokeProjectAdmin(projectId, userId, tenant);
        return ResponseEntity.ok(new ApiResponse("Project admin revoked successfully", 200));
    }

    @GetMapping("/user/{userId}/admin-of")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<ProjectViewDto>> getProjectsWhereUserIsAdmin(
            @PathVariable Long userId,
            @CurrentTenant Tenant tenant
    ) {
        return ResponseEntity.ok(projectService.getProjectsWhereUserIsAdmin(userId, tenant));
    }

    @GetMapping("/{projectId}/members")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'ADMIN')")
    public ResponseEntity<List<ProjectMemberDto>> getProjectMembers(
            @PathVariable Long projectId,
            @CurrentTenant Tenant tenant
    ) {
        return ResponseEntity.ok(projectService.getProjectMembers(projectId, tenant));
    }

    @PostMapping("/{projectId}/members")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'ADMIN')")
    public ResponseEntity<ApiResponse> addProjectMember(
            @PathVariable Long projectId,
            @Valid @RequestBody AddProjectMemberRequest request,
            @CurrentTenant Tenant tenant
    ) {
        projectService.addProjectMember(projectId, request.userId(), request.roleName(), tenant);
        return ResponseEntity.ok(new ApiResponse("Member added successfully", 200));
    }

    @PutMapping("/{projectId}/members/{userId}/role")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'ADMIN')")
    public ResponseEntity<ApiResponse> updateProjectMemberRole(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateProjectMemberRoleRequest request,
            @CurrentTenant Tenant tenant
    ) {
        projectService.updateProjectMemberRole(projectId, userId, request.roleName(), tenant);
        return ResponseEntity.ok(new ApiResponse("Role updated successfully", 200));
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'ADMIN')")
    public ResponseEntity<ApiResponse> removeProjectMember(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @CurrentTenant Tenant tenant
    ) {
        projectService.removeProjectMember(projectId, userId, tenant);
        return ResponseEntity.ok(new ApiResponse("Member removed successfully", 200));
    }

    @PostMapping("/{projectId}/favorite")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> addProjectToFavorites(
            @PathVariable Long projectId,
            @CurrentUser User user,
            @CurrentTenant Tenant tenant
    ) {
        projectService.addProjectToFavorites(projectId, user, tenant);
        return ResponseEntity.ok(new ApiResponse("Project added to favorites", 200));
    }

    @DeleteMapping("/{projectId}/favorite")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> removeProjectFromFavorites(
            @PathVariable Long projectId,
            @CurrentUser User user,
            @CurrentTenant Tenant tenant
    ) {
        projectService.removeProjectFromFavorites(projectId, user, tenant);
        return ResponseEntity.ok(new ApiResponse("Project removed from favorites", 200));
    }

    @GetMapping("/favorites")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProjectViewDto>> getFavoriteProjects(
            @CurrentUser User user,
            @CurrentTenant Tenant tenant
    ) {
        return ResponseEntity.ok(projectService.getFavoriteProjects(user, tenant));
    }

    @GetMapping("/{projectId}/is-favorite")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> isProjectFavorite(
            @PathVariable Long projectId,
            @CurrentUser User user,
            @CurrentTenant Tenant tenant
    ) {
        return ResponseEntity.ok(projectService.isProjectFavorite(projectId, user, tenant));
    }

    @PutMapping("/{projectId}/item-type-set")
    @PreAuthorize("hasRole('TENANT_ADMIN') or @projectSecurityService.hasProjectRole(#projectId, 'PROJECT_ADMIN')")
    public ResponseEntity<ApiResponse> assignItemTypeSet(
            @PathVariable Long projectId,
            @RequestParam Long itemTypeSetId,
            @CurrentTenant Tenant tenant,
            @CurrentUser User user
    ) {
        projectService.assignItemTypeSet(tenant, projectId, itemTypeSetId, user);
        return ResponseEntity.ok(new ApiResponse("ItemTypeSet assigned successfully", 200));
    }

    @GetMapping("/available-item-type-sets")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<List<ItemTypeSetViewDto>> getAvailableItemTypeSets(
            @CurrentTenant Tenant tenant
    ) {
        return ResponseEntity.ok(itemTypeSetService.getAllGlobalItemTypeSets(tenant));
    }
}

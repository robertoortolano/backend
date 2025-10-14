package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.security.CurrentTenant;
import com.example.demo.security.CurrentUser;
import com.example.demo.service.TenantUserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/users")
@RequiredArgsConstructor
public class TenantUserManagementController {

    private final TenantUserManagementService tenantUserManagementService;

    /**
     * GET /api/tenant/users - Ottiene tutti gli utenti con accesso alla tenant corrente
     * Accessibile solo agli ADMIN
     */
    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<TenantUserDto>> getAllUsersWithAccess(
            @CurrentTenant Tenant tenant) {
        List<TenantUserDto> users = tenantUserManagementService.getAllUsersWithAccess(tenant);
        return ResponseEntity.ok(users);
    }

    /**
     * GET /api/tenant/users/check?username={username} - Verifica lo stato di accesso di un utente
     * Accessibile solo agli ADMIN
     */
    @GetMapping("/check")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<UserAccessStatusDto> checkUserAccess(
            @RequestParam String username,
            @CurrentTenant Tenant tenant) {
        try {
            UserAccessStatusDto status = tenantUserManagementService.getUserAccessStatus(username, tenant);
            return ResponseEntity.ok(status);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /api/tenant/users/assign-role - Assegna un ruolo (ADMIN o USER) a un nuovo utente
     * Accessibile solo agli ADMIN
     */
    @PostMapping("/assign-role")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse> assignRole(
            @Valid @RequestBody AssignRoleRequest request,
            @CurrentTenant Tenant tenant,
            @CurrentUser User currentUser) {
        try {
            tenantUserManagementService.assignRole(request.username(), request.roleName(), tenant, currentUser);
            return ResponseEntity.ok(new ApiResponse(
                    "Role " + request.roleName() + " assigned successfully to " + request.username(),
                    HttpStatus.OK.value()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.CONFLICT.value()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.FORBIDDEN.value()));
        }
    }

    /**
     * POST /api/tenant/users/grant - Concede accesso USER a un utente (backward compatibility)
     * Accessibile solo agli ADMIN
     */
    @PostMapping("/grant")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse> grantUserAccess(
            @Valid @RequestBody GrantUserAccessRequest request,
            @CurrentTenant Tenant tenant,
            @CurrentUser User currentUser) {
        try {
            tenantUserManagementService.grantUserAccess(request.username(), tenant, currentUser);
            return ResponseEntity.ok(new ApiResponse(
                    "User access granted successfully",
                    HttpStatus.OK.value()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.CONFLICT.value()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.FORBIDDEN.value()));
        }
    }
    
    /**
     * PUT /api/tenant/users/{userId}/roles - Aggiorna i ruoli di un utente esistente
     * Supporta assegnazione multipla di ruoli (es: ["ADMIN", "USER"])
     * Accessibile solo agli ADMIN
     */
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse> updateUserRoles(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRolesRequest request,
            @CurrentTenant Tenant tenant,
            @CurrentUser User currentUser) {
        try {
            tenantUserManagementService.updateUserRoles(request.userId(), request.roleNames(), tenant, currentUser);
            return ResponseEntity.ok(new ApiResponse(
                    "User roles updated successfully",
                    HttpStatus.OK.value()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.BAD_REQUEST.value()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.CONFLICT.value()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.FORBIDDEN.value()));
        }
    }
    
    /**
     * @deprecated Usa updateUserRoles() invece.
     * PUT /api/tenant/users/change-role - Cambia il ruolo di un utente esistente
     * Accessibile solo agli ADMIN
     */
    @Deprecated
    @PutMapping("/change-role")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse> changeUserRole(
            @Valid @RequestBody ChangeUserRoleRequest request,
            @CurrentTenant Tenant tenant,
            @CurrentUser User currentUser) {
        try {
            tenantUserManagementService.changeUserRole(request.userId(), request.newRoleName(), tenant, currentUser);
            return ResponseEntity.ok(new ApiResponse(
                    "User role changed successfully to " + request.newRoleName(),
                    HttpStatus.OK.value()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.CONFLICT.value()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.FORBIDDEN.value()));
        }
    }

    /**
     * DELETE /api/tenant/users/revoke - Revoca accesso di un utente
     * Accessibile solo agli ADMIN
     */
    @DeleteMapping("/revoke")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse> revokeUserAccess(
            @Valid @RequestBody RevokeUserAccessRequest request,
            @CurrentTenant Tenant tenant,
            @CurrentUser User currentUser) {
        try {
            tenantUserManagementService.revokeUserAccess(request.userId(), tenant, currentUser);
            return ResponseEntity.ok(new ApiResponse(
                    "User access revoked successfully",
                    HttpStatus.OK.value()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.CONFLICT.value()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse(e.getMessage(), HttpStatus.FORBIDDEN.value()));
        }
    }

    /**
     * GET /api/tenant/users/{userId}/can-revoke - Verifica se un utente può essere rimosso
     * Accessibile solo agli ADMIN
     */
    @GetMapping("/{userId}/can-revoke")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<Boolean> canRevokeAccess(
            @PathVariable Long userId,
            @CurrentTenant Tenant tenant) {
        boolean canRevoke = tenantUserManagementService.canRevokeAccess(userId, tenant);
        return ResponseEntity.ok(canRevoke);
    }
}


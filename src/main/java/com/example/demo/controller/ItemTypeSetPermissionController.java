package com.example.demo.controller;

import com.example.demo.entity.Tenant;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.ItemTypeSetPermissionService;
import com.example.demo.dto.RoleViewDto;
import com.example.demo.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/itemtypeset-permissions")
@RequiredArgsConstructor
public class ItemTypeSetPermissionController {
    
    private final ItemTypeSetPermissionService itemTypeSetPermissionService;
    private final RoleService roleService;
    
    /**
     * Ottiene tutte le permissions per un ItemTypeSet
     */
    @GetMapping("/itemtypeset/{itemTypeSetId}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getPermissionsByItemTypeSet(
            @PathVariable Long itemTypeSetId,
            @CurrentTenant Tenant tenant) {
        try {
            Map<String, List<Map<String, Object>>> permissions = 
                itemTypeSetPermissionService.getPermissionsByItemTypeSet(itemTypeSetId, tenant);
            return ResponseEntity.ok(permissions);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Crea automaticamente tutte le permissions per un ItemTypeSet
     */
    @PostMapping("/create-for-itemtypeset/{itemTypeSetId}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<String> createPermissionsForItemTypeSet(
            @PathVariable Long itemTypeSetId,
            @CurrentTenant Tenant tenant) {
        try {
            itemTypeSetPermissionService.createPermissionsForItemTypeSet(itemTypeSetId, tenant);
            return ResponseEntity.ok("Permissions created successfully for ItemTypeSet: " + itemTypeSetId);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error creating permissions: " + e.getMessage());
        }
    }
    
    
    /**
     * Assegna un ruolo a una permission
     */
    @PostMapping("/assign-role")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<String> assignRoleToPermission(
            @RequestParam Long permissionId,
            @RequestParam Long roleId,
            @RequestParam(required = false) String permissionType,
            @CurrentTenant Tenant tenant) {
        try {
            itemTypeSetPermissionService.assignRoleToPermission(permissionId, roleId, permissionType);
            return ResponseEntity.ok("Role assigned successfully to permission");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error assigning role: " + e.getMessage());
        }
    }
    
    
    
    /**
     * Ottiene tutti i ruoli disponibili
     */
    @GetMapping("/roles")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<List<RoleViewDto>> getAvailableRoles(@CurrentTenant Tenant tenant) {
        try {
            List<RoleViewDto> roles = roleService.getAllTenantRoles(tenant);
            return ResponseEntity.ok(roles);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Rimuove un ruolo da una permission
     */
    @DeleteMapping("/remove-role")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<String> removeRoleFromPermission(
            @RequestParam Long permissionId,
            @RequestParam Long roleId,
            @RequestParam(required = false) String permissionType,
            @CurrentTenant Tenant tenant) {
        try {
            itemTypeSetPermissionService.removeRoleFromPermission(permissionId, roleId, permissionType);
            return ResponseEntity.ok("Role removed successfully from permission");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error removing role: " + e.getMessage());
        }
    }
}

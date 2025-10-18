package com.example.demo.controller;

import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.entity.Group;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.GroupRepository;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.ItemTypeSetPermissionService;
import com.example.demo.dto.UserSimpleDto;
import com.example.demo.dto.GroupSimpleDto;
import com.example.demo.dto.RoleViewDto;
import com.example.demo.mapper.DtoMapperFacade;
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
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final DtoMapperFacade dtoMapper;
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
     * Assegna una grant a una permission
     */
    @PostMapping("/assign-grant")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<String> assignGrantToPermission(
            @RequestParam Long permissionId,
            @RequestParam(required = false) String permissionType,
            @RequestBody Map<String, Object> grantData,
            @CurrentTenant Tenant tenant) {
        try {
            System.out.println("DEBUG: Received permissionId=" + permissionId);
            System.out.println("DEBUG: Received permissionType=" + permissionType);
            System.out.println("DEBUG: Received grantData=" + grantData);
            itemTypeSetPermissionService.assignGrantToPermission(permissionId, permissionType, grantData);
            return ResponseEntity.ok("Grant assigned successfully to permission");
        } catch (Exception e) {
            log.error("Error assigning grant to permission", e);
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.internalServerError()
                    .body("Error assigning grant: " + errorMsg);
        }
    }
    
    /**
     * Ottiene tutti gli utenti disponibili
     */
    @GetMapping("/users")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<List<UserSimpleDto>> getAvailableUsers(@CurrentTenant Tenant tenant) {
        try {
            List<User> users = userRepository.findAll();
            List<UserSimpleDto> userDtos = dtoMapper.toUserSimpleDtos(users);
            return ResponseEntity.ok(userDtos);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Ottiene tutti i gruppi disponibili
     */
    @GetMapping("/groups")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<List<GroupSimpleDto>> getAvailableGroups(@CurrentTenant Tenant tenant) {
        try {
            List<Group> groups = groupRepository.findByTenant(tenant);
            List<GroupSimpleDto> groupDtos = dtoMapper.toGroupSimpleDtos(groups);
            return ResponseEntity.ok(groupDtos);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
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

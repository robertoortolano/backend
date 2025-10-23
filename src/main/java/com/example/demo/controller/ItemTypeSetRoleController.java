package com.example.demo.controller;

import com.example.demo.dto.ItemTypeSetRoleDTO;
import com.example.demo.dto.ItemTypeSetRoleCreateDTO;
import com.example.demo.entity.Tenant;
import com.example.demo.enums.ItemTypeSetRoleType;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.ItemTypeSetRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/itemtypeset-roles")
//@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ItemTypeSetRoleController {
    
    private final ItemTypeSetRoleService itemTypeSetRoleService;
    
    /**
     * Crea automaticamente tutti i ruoli per un ItemTypeSet
     */
    @PostMapping("/create-for-itemtypeset/{itemTypeSetId}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<String> createRolesForItemTypeSet(
            @PathVariable Long itemTypeSetId,
            @CurrentTenant Tenant tenant) {
        try {
            itemTypeSetRoleService.createRolesForItemTypeSet(itemTypeSetId, tenant);
            return ResponseEntity.ok("Roles created successfully for ItemTypeSet: " + itemTypeSetId);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating roles: " + e.getMessage());
        }
    }
    
    /**
     * Crea un ruolo manualmente
     */
    @PostMapping
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<ItemTypeSetRoleDTO> createRole(
            @RequestBody ItemTypeSetRoleCreateDTO createDTO,
            @CurrentTenant Tenant tenant) {
        try {
            ItemTypeSetRoleDTO role = itemTypeSetRoleService.createRole(createDTO, tenant);
            return ResponseEntity.ok(role);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Ottiene tutti i ruoli per un ItemTypeSet
     */
    @GetMapping("/itemtypeset/{itemTypeSetId}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<List<ItemTypeSetRoleDTO>> getRolesByItemTypeSet(
            @PathVariable Long itemTypeSetId,
            @CurrentTenant Tenant tenant) {
        try {
            List<ItemTypeSetRoleDTO> roles = itemTypeSetRoleService.getRolesByItemTypeSet(itemTypeSetId, tenant);
            return ResponseEntity.ok(roles);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Ottiene i ruoli per tipo specifico
     */
    @GetMapping("/itemtypeset/{itemTypeSetId}/type/{roleType}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<List<ItemTypeSetRoleDTO>> getRolesByType(
            @PathVariable Long itemTypeSetId,
            @PathVariable ItemTypeSetRoleType roleType,
            @CurrentTenant Tenant tenant) {
        try {
            List<ItemTypeSetRoleDTO> roles = itemTypeSetRoleService.getRolesByType(itemTypeSetId, roleType, tenant);
            return ResponseEntity.ok(roles);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    
    
    /**
     * Assegna un Grant diretto a un ruolo specifico
     */
    @PostMapping("/assign-grant-direct")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<ItemTypeSetRoleDTO> assignGrantDirect(
            @RequestParam Long roleId,
            @RequestParam Long grantId,
            @CurrentTenant Tenant tenant) {
        try {
            ItemTypeSetRoleDTO result = itemTypeSetRoleService.assignGrantDirect(roleId, grantId, tenant);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    /**
     * Assegna un Role template a un ruolo specifico
     */
    @PostMapping("/assign-role-template")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<ItemTypeSetRoleDTO> assignRoleTemplate(
            @RequestParam Long roleId,
            @RequestParam Long roleTemplateId,
            @CurrentTenant Tenant tenant) {
        try {
            ItemTypeSetRoleDTO result = itemTypeSetRoleService.assignRoleTemplate(roleId, roleTemplateId, tenant);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    /**
     * Rimuove l'assegnazione (Grant o Role) da un ruolo specifico
     */
    @DeleteMapping("/remove-assignment")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<String> removeAssignment(
            @RequestParam Long roleId,
            @CurrentTenant Tenant tenant) {
        try {
            itemTypeSetRoleService.removeAssignment(roleId, tenant);
            return ResponseEntity.ok("Assignment removed successfully from role");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error removing assignment: " + e.getMessage());
        }
    }
    
    /**
     * Elimina tutti i ruoli per un ItemTypeSet
     */
    @DeleteMapping("/itemtypeset/{itemTypeSetId}/all")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<String> deleteAllRolesForItemTypeSet(
            @PathVariable Long itemTypeSetId,
            @CurrentTenant Tenant tenant) {
        try {
            itemTypeSetRoleService.deleteAllRolesForItemTypeSet(itemTypeSetId, tenant);
            return ResponseEntity.ok("All roles deleted successfully for ItemTypeSet: " + itemTypeSetId);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting roles: " + e.getMessage());
        }
    }
    
    /**
     * Ottiene i ruoli per entità specifica
     */
    @GetMapping("/itemtypeset/{itemTypeSetId}/entity/{entityType}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<List<ItemTypeSetRoleDTO>> getRolesByEntityType(
            @PathVariable Long itemTypeSetId,
            @PathVariable String entityType,
            @CurrentTenant Tenant tenant) {
        try {
            // Implementazione per ottenere ruoli per tipo di entità specifico
            // Questo richiederebbe un metodo aggiuntivo nel service
            return ResponseEntity.ok(List.of());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

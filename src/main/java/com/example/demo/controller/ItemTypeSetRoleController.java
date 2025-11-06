package com.example.demo.controller;

import com.example.demo.dto.ItemTypeSetRoleDTO;
import com.example.demo.dto.ItemTypeSetRoleCreateDTO;
import com.example.demo.dto.ItemTypeSetRoleGrantCreateDto;
import com.example.demo.entity.Tenant;
import com.example.demo.enums.ItemTypeSetRoleType;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.ItemTypeSetRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
        itemTypeSetRoleService.createRolesForItemTypeSet(itemTypeSetId, tenant);
        return ResponseEntity.ok("Roles created successfully for ItemTypeSet: " + itemTypeSetId);
    }
    
    /**
     * Crea un ruolo manualmente
     */
    @PostMapping
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<ItemTypeSetRoleDTO> createRole(
            @RequestBody ItemTypeSetRoleCreateDTO createDTO,
            @CurrentTenant Tenant tenant) {
        ItemTypeSetRoleDTO role = itemTypeSetRoleService.createRole(createDTO, tenant);
        return ResponseEntity.ok(role);
    }
    
    /**
     * Ottiene tutti i ruoli per un ItemTypeSet
     */
    @GetMapping("/itemtypeset/{itemTypeSetId}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<List<ItemTypeSetRoleDTO>> getRolesByItemTypeSet(
            @PathVariable Long itemTypeSetId,
            @CurrentTenant Tenant tenant) {
        List<ItemTypeSetRoleDTO> roles = itemTypeSetRoleService.getRolesByItemTypeSet(itemTypeSetId, tenant);
        return ResponseEntity.ok(roles);
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
        List<ItemTypeSetRoleDTO> roles = itemTypeSetRoleService.getRolesByType(itemTypeSetId, roleType, tenant);
        return ResponseEntity.ok(roles);
    }
    
    
    
    /**
     * Assegna un Grant diretto a un ruolo specifico (usando un Grant esistente)
     */
    @PostMapping("/assign-grant-direct")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<ItemTypeSetRoleDTO> assignGrantDirect(
            @RequestParam Long roleId,
            @RequestParam Long grantId,
            @CurrentTenant Tenant tenant) {
        ItemTypeSetRoleDTO result = itemTypeSetRoleService.assignGrantDirect(roleId, grantId, tenant);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Crea un Grant e lo assegna direttamente a un ItemTypeSetRole.
     * Il Grant viene creato al momento dell'assegnazione con gli utenti/gruppi specificati.
     */
    @PostMapping("/create-and-assign-grant")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<ItemTypeSetRoleDTO> createAndAssignGrant(
            @Valid @RequestBody ItemTypeSetRoleGrantCreateDto dto,
            @CurrentTenant Tenant tenant) {
        ItemTypeSetRoleDTO result = itemTypeSetRoleService.createAndAssignGrant(dto, tenant);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Recupera i dettagli di un Grant assegnato a un ItemTypeSetRole
     */
    @GetMapping("/{roleId}/grant-details")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<com.example.demo.dto.GrantDetailsDto> getGrantDetails(
            @PathVariable Long roleId,
            @CurrentTenant Tenant tenant) {
        com.example.demo.dto.GrantDetailsDto result = itemTypeSetRoleService.getGrantDetails(roleId, tenant);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Aggiorna un Grant esistente assegnato a un ItemTypeSetRole
     */
    @PutMapping("/update-grant")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<ItemTypeSetRoleDTO> updateGrant(
            @Valid @RequestBody ItemTypeSetRoleGrantCreateDto dto,
            @CurrentTenant Tenant tenant) {
        ItemTypeSetRoleDTO result = itemTypeSetRoleService.updateGrant(dto, tenant);
        return ResponseEntity.ok(result);
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
        ItemTypeSetRoleDTO result = itemTypeSetRoleService.assignRoleTemplate(roleId, roleTemplateId, tenant);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Rimuove l'assegnazione (Grant o Role) da un ruolo specifico
     */
    @DeleteMapping("/remove-assignment")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<String> removeAssignment(
            @RequestParam Long roleId,
            @CurrentTenant Tenant tenant) {
        itemTypeSetRoleService.removeAssignment(roleId, tenant);
        return ResponseEntity.ok("Assignment removed successfully from role");
    }
    
    /**
     * Elimina tutti i ruoli per un ItemTypeSet
     */
    @DeleteMapping("/itemtypeset/{itemTypeSetId}/all")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<String> deleteAllRolesForItemTypeSet(
            @PathVariable Long itemTypeSetId,
            @CurrentTenant Tenant tenant) {
        itemTypeSetRoleService.deleteAllRolesForItemTypeSet(itemTypeSetId, tenant);
        return ResponseEntity.ok("All roles deleted successfully for ItemTypeSet: " + itemTypeSetId);
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
        // Implementazione per ottenere ruoli per tipo di entità specifico
        // Questo richiederebbe un metodo aggiuntivo nel service
        return ResponseEntity.ok(List.of());
    }
}

package com.example.demo.controller;

import com.example.demo.dto.ItemTypeSetCreateDto;
import com.example.demo.dto.ItemTypeSetUpdateDto;
import com.example.demo.dto.ItemTypeSetViewDto;
import com.example.demo.entity.Tenant;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.ItemTypeSetService;
import com.example.demo.service.ItemTypeSetRoleService;
import com.example.demo.service.ItemTypeSetPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/item-type-sets")
@RequiredArgsConstructor
@Slf4j
public class ItemTypeSetController {

    private final ItemTypeSetService itemTypeSetService;
    private final DtoMapperFacade dtoMapper;
    private final ItemTypeSetRoleService itemTypeSetRoleService;
    private final ItemTypeSetPermissionService itemTypeSetPermissionService;

    @GetMapping("/global")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<List<ItemTypeSetViewDto>> getGlobalItemTypeSets(
            @CurrentTenant Tenant tenant
    ) {
        List<ItemTypeSetViewDto> sets = itemTypeSetService.getAllGlobalItemTypeSets(tenant);
        return ResponseEntity.ok(sets);
    }

    @GetMapping("/project")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<List<ItemTypeSetViewDto>> getProjectItemTypeSet(
            @CurrentTenant Tenant tenant
    ) {
        List<ItemTypeSetViewDto> sets = itemTypeSetService.getAllProjectItemTypeSets(tenant);
        return ResponseEntity.ok(sets);
    }

    // 1. CREATE
    @PostMapping
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<ItemTypeSetViewDto> createGlobal(
            @RequestBody ItemTypeSetCreateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        ItemTypeSetViewDto created = itemTypeSetService.createGlobal(tenant, dto);
        
        // Crea automaticamente le permissions per il nuovo ItemTypeSet
        try {
            itemTypeSetPermissionService.createPermissionsForItemTypeSet(created.id(), tenant);
        } catch (Exception e) {
            // Log dell'errore ma non bloccare la creazione dell'ItemTypeSet
            log.error("Error creating permissions for ItemTypeSet {}", created.id(), e);
        }
        
        return ResponseEntity.ok(created);
    }



    // 2. READ BY ID
    @GetMapping("/{id}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<ItemTypeSetViewDto> getById(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant
    ) {
        ItemTypeSetViewDto dto = itemTypeSetService.getById(tenant, id);
        return ResponseEntity.ok(dto);
    }

    // 3. UPDATE
    @PutMapping("/{id}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<ItemTypeSetViewDto> update(
            @PathVariable Long id,
            @RequestBody ItemTypeSetUpdateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        ItemTypeSetViewDto updated = itemTypeSetService.updateItemTypeSet(tenant, id, dto);
        return ResponseEntity.ok(updated);
    }

    // 4. DELETE
    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant
    ) {
        itemTypeSetService.deleteItemTypeSet(tenant, id);
        return ResponseEntity.noContent().build();
    }

    // ========================
    // PROJECT ITEM TYPE SETS
    // ========================

    @GetMapping("/project/{projectId}")
    @PreAuthorize("@securityService.canCreateFieldSet(principal, #tenant, #projectId)")
    public ResponseEntity<List<ItemTypeSetViewDto>> getProjectItemTypeSets(
            @PathVariable Long projectId,
            @CurrentTenant Tenant tenant
    ) {
        return ResponseEntity.ok(itemTypeSetService.getProjectItemTypeSets(tenant, projectId));
    }

    @PostMapping("/project/{projectId}")
    @PreAuthorize("@securityService.canCreateFieldSet(principal, #tenant, #projectId)")
    public ResponseEntity<ItemTypeSetViewDto> createProjectItemTypeSet(
            @PathVariable Long projectId,
            @RequestBody ItemTypeSetCreateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        ItemTypeSetViewDto created = itemTypeSetService.createForProject(tenant, projectId, dto);
        
        // Crea automaticamente le permissions per il nuovo ItemTypeSet
        try {
            itemTypeSetPermissionService.createPermissionsForItemTypeSet(created.id(), tenant);
        } catch (Exception e) {
            log.error("Error creating permissions for ItemTypeSet {}", created.id(), e);
        }
        
        return ResponseEntity.ok(created);
    }

    @GetMapping("/project/{projectId}/{id}")
    @PreAuthorize("@securityService.canCreateFieldSet(principal, #tenant, #projectId)")
    public ResponseEntity<ItemTypeSetViewDto> getProjectItemTypeSetById(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @CurrentTenant Tenant tenant
    ) {
        ItemTypeSetViewDto dto = itemTypeSetService.getById(tenant, id);
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/project/{projectId}/{id}")
    @PreAuthorize("@securityService.canCreateFieldSet(principal, #tenant, #projectId)")
    public ResponseEntity<ItemTypeSetViewDto> updateProjectItemTypeSet(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @RequestBody ItemTypeSetUpdateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        ItemTypeSetViewDto updated = itemTypeSetService.updateProjectItemTypeSet(tenant, projectId, id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/project/{projectId}/{id}")
    @PreAuthorize("@securityService.canCreateFieldSet(principal, #tenant, #projectId)")
    public ResponseEntity<Void> deleteProjectItemTypeSet(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @CurrentTenant Tenant tenant
    ) {
        itemTypeSetService.deleteItemTypeSet(tenant, id);
        return ResponseEntity.noContent().build();
    }

    // ========================
    // IMPACT ANALYSIS
    // ========================

    /**
     * Analizza gli impatti della rimozione di ItemTypeConfiguration da un ItemTypeSet
     */
    @PostMapping("/{itemTypeSetId}/analyze-itemtypeconfiguration-removal-impact")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<com.example.demo.dto.ItemTypeConfigurationRemovalImpactDto> analyzeItemTypeConfigurationRemovalImpact(
            @PathVariable Long itemTypeSetId,
            @RequestBody java.util.List<Long> removedItemTypeConfigurationIds,
            @CurrentTenant Tenant tenant
    ) {
        java.util.Set<Long> configIdsSet = new java.util.HashSet<>(removedItemTypeConfigurationIds);
        com.example.demo.dto.ItemTypeConfigurationRemovalImpactDto impact = itemTypeSetService.analyzeItemTypeConfigurationRemovalImpact(
                tenant, itemTypeSetId, configIdsSet);
        return ResponseEntity.ok(impact);
    }
    
    /**
     * Rimuove le permission orfane per le ItemTypeConfiguration rimosse
     */
    @PostMapping("/{itemTypeSetId}/remove-itemtypeconfiguration-permissions")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<String> removeItemTypeConfigurationPermissions(
            @PathVariable Long itemTypeSetId,
            @RequestBody RemoveItemTypeConfigurationPermissionsRequest request,
            @CurrentTenant Tenant tenant
    ) {
        java.util.Set<Long> removedConfigIds = request.removedItemTypeConfigurationIds() != null
                ? new java.util.HashSet<>(request.removedItemTypeConfigurationIds())
                : new java.util.HashSet<>();
        java.util.Set<Long> preservedPermissionIds = request.preservedPermissionIds() != null
                ? new java.util.HashSet<>(request.preservedPermissionIds())
                : new java.util.HashSet<>();
        
        itemTypeSetService.removeOrphanedPermissionsForItemTypeConfigurations(
                tenant, itemTypeSetId, removedConfigIds, preservedPermissionIds);
        
        return ResponseEntity.ok("Permissions orfane rimosse con successo");
    }
    
    /**
     * Request DTO per la rimozione delle permission orfane
     */
    public record RemoveItemTypeConfigurationPermissionsRequest(
            java.util.List<Long> removedItemTypeConfigurationIds,
            java.util.List<Long> preservedPermissionIds // Lista di permission IDs da preservare (non rimuovere)
    ) {}
}

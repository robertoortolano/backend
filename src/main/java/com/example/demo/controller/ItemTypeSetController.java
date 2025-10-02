package com.example.demo.controller;

import com.example.demo.dto.ItemTypeSetCreateDto;
import com.example.demo.dto.ItemTypeSetUpdateDto;
import com.example.demo.dto.ItemTypeSetViewDto;
import com.example.demo.entity.Tenant;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.ItemTypeSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/item-type-sets")
@RequiredArgsConstructor
public class ItemTypeSetController {

    private final ItemTypeSetService itemTypeSetService;
    private final DtoMapperFacade dtoMapper;

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
}

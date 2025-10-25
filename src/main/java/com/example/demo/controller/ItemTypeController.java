package com.example.demo.controller;

import com.example.demo.dto.ItemTypeCreateDto;
import com.example.demo.dto.ItemTypeDetailDto;
import com.example.demo.dto.ItemTypeViewDto;
import com.example.demo.entity.Tenant;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.ItemTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/item-types")
@RequiredArgsConstructor
public class ItemTypeController {

    private final ItemTypeService itemTypeService;

    @PostMapping
    @PreAuthorize("@securityService.canCreateItemType(principal, #tenant)")
    public ResponseEntity<ItemTypeViewDto> createItemType(
            @Valid @RequestBody ItemTypeCreateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        ItemTypeViewDto created = itemTypeService.createItemType(tenant, dto);
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/{itemTypeId}")
    @PreAuthorize("@securityService.canDeleteItemType(principal, #tenant, #itemTypeId)")
    public ResponseEntity<Void> deleteItemType(
            @PathVariable Long itemTypeId,
            @CurrentTenant Tenant tenant
    ) {
        itemTypeService.deleteItemType(tenant, itemTypeId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{itemTypeId}")
    @PreAuthorize("@securityService.canEditItemType(principal, #tenant, #itemTypeId)")
    public ResponseEntity<ItemTypeViewDto> updateItemType(
            @PathVariable Long itemTypeId,
            @RequestBody ItemTypeCreateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        ItemTypeViewDto updated = itemTypeService.updateItemType(tenant, itemTypeId, dto);
        return ResponseEntity.ok(updated);
    }

    @GetMapping
    @PreAuthorize("@securityService.canCreateItemType(principal, #tenant)")
    public List<ItemTypeViewDto> getItemTypes(@CurrentTenant Tenant tenant) {
        return itemTypeService.getAllForTenant(tenant);
    }

    @GetMapping("/{itemTypeId}")
    @PreAuthorize("@securityService.canCreateItemType(principal, #tenant)")
    public ResponseEntity<ItemTypeViewDto> getItemTypeById(
            @PathVariable Long itemTypeId,
            @CurrentTenant Tenant tenant
    ) {
        ItemTypeViewDto dto = itemTypeService.getById(tenant, itemTypeId);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{itemTypeId}/details")
    @PreAuthorize("@securityService.canCreateItemType(principal, #tenant)")
    public ResponseEntity<ItemTypeDetailDto> getItemTypeDetails(
            @PathVariable Long itemTypeId,
            @CurrentTenant Tenant tenant
    ) {
        ItemTypeDetailDto dto = itemTypeService.getItemTypeDetail(itemTypeId, tenant);
        return ResponseEntity.ok(dto);
    }

}


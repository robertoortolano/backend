package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.Tenant;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.FieldSetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/field-sets")
@RequiredArgsConstructor
public class FieldSetController {

    private final FieldSetService fieldSetService;

    @PostMapping
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<FieldSetViewDto> createGlobal(
            @Valid @RequestBody FieldSetCreateDto dto,
            @CurrentTenant Tenant tenant) {
        FieldSetViewDto created = fieldSetService.createGlobalFieldSet(dto,tenant);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.canEditFieldSet(principal, #tenant, #id)")
    public ResponseEntity<FieldSetViewDto> update(
            @PathVariable Long id,
            @RequestBody FieldSetCreateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        FieldSetViewDto updated = fieldSetService.updateFieldSet(tenant, id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.canDeleteFieldSet(principal, #tenant, #id)")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant
    ) {
        fieldSetService.delete(tenant, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.canViewFieldSet(principal, #tenant, #id)")
    public ResponseEntity<FieldSetViewDto> getById(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant
    ) {
        return ResponseEntity.ok(fieldSetService.getById(tenant, id));
    }

    @GetMapping
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<List<FieldSetViewDto>> getAllFieldSets(
            @CurrentTenant Tenant tenant
    ) {
        return ResponseEntity.ok(fieldSetService.getGlobalFieldSets(tenant));
    }

    @PutMapping("/{fieldSetId}/entries/reorder")
    @PreAuthorize("@securityService.canEditFieldSet(principal, #tenant, #fieldSetId)")
    public ResponseEntity<Void> reorderEntries(
            @PathVariable Long fieldSetId,
            @RequestBody List<EntryOrderDto> newOrder,
            @CurrentTenant Tenant tenant
    ) {
        fieldSetService.reorderEntries(tenant, fieldSetId, newOrder);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{fieldSetId}/entries")
    @PreAuthorize("@securityService.canEditFieldSet(principal, #tenant, #fieldSetId)")
    public ResponseEntity<FieldSetEntryViewDto> addEntry(
            @PathVariable Long fieldSetId,
            @RequestBody FieldSetEntryCreateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        FieldSetEntryViewDto created = fieldSetService.addEntry(tenant, fieldSetId, dto);
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/entries/{entryId}")
    @PreAuthorize("@securityService.canDeleteFieldSetEntry(principal, #tenant, #entryId)")
    public ResponseEntity<Void> deleteFieldSetEntry(
            @PathVariable Long entryId,
            @CurrentTenant Tenant tenant
    ) {
        fieldSetService.deleteEntry(tenant, entryId);
        return ResponseEntity.noContent().build();
    }

    // ========================
    // PROJECT FIELD SETS
    // ========================

    // Get project field sets
    @GetMapping("/project/{projectId}")
    @PreAuthorize("@securityService.canCreateFieldSet(principal, #tenant, #projectId)")
    public ResponseEntity<List<FieldSetViewDto>> getProjectFieldSets(
            @PathVariable Long projectId,
            @CurrentTenant Tenant tenant
    ) {
        return ResponseEntity.ok(fieldSetService.getProjectFieldSets(tenant, projectId));
    }

    // Create project field set
    @PostMapping("/project/{projectId}")
    @PreAuthorize("@securityService.canCreateFieldSet(principal, #tenant, #projectId)")
    public ResponseEntity<FieldSetViewDto> createProjectFieldSet(
            @PathVariable Long projectId,
            @Valid @RequestBody FieldSetCreateDto dto,
            @CurrentTenant Tenant tenant
    ) {
        FieldSetViewDto created = fieldSetService.createProjectFieldSet(tenant, projectId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

}


package com.example.demo.controller;

import com.example.demo.dto.FieldCreateDto;
import com.example.demo.dto.FieldViewDto;
import com.example.demo.entity.Tenant;
import com.example.demo.dto.FieldDetailDto;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.FieldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fields")
@RequiredArgsConstructor
public class FieldController {

    private final FieldService fieldService;

    @PostMapping
    @PreAuthorize("@securityService.canCreateField(principal, #tenant)")
    public ResponseEntity<FieldViewDto> createField(
            @Valid @RequestBody FieldCreateDto requestDto,
            @CurrentTenant Tenant tenant
    ) {
        FieldViewDto created = fieldService.createField(requestDto, tenant);
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.canDeleteField(principal, #tenant , #id)")
    public ResponseEntity<Void> deleteField(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant
    ) {
        fieldService.deleteField(id, tenant);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.canEditField(principal, #tenant , #id)")
    public ResponseEntity<FieldViewDto> updateField(
            @PathVariable Long id,
            @RequestBody FieldCreateDto requestDto,
            @CurrentTenant Tenant tenant) {
        FieldViewDto updated = fieldService.updateField(tenant, id, requestDto);
        return ResponseEntity.ok(updated);
    }


    @GetMapping("/details")
    @PreAuthorize("@securityService.canCreateField(principal, #tenant)")
    public ResponseEntity<List<FieldDetailDto>> getFieldsDetails(@CurrentTenant Tenant tenant) {
        return ResponseEntity.ok(fieldService.getFieldsDetails(tenant));
    }


    @GetMapping
    @PreAuthorize("@securityService.canCreateField(principal, #tenant)")
    public ResponseEntity<List<FieldViewDto>> getFields(@CurrentTenant Tenant tenant) {
        return ResponseEntity.ok(fieldService.getFields(tenant));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.canCreateField(principal, #tenant)")
    public ResponseEntity<FieldViewDto> getFieldById(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant) {
        return ResponseEntity.ok(fieldService.getById(id, tenant));
    }

    @GetMapping("/{id}/details")
    @PreAuthorize("@securityService.canCreateField(principal, #tenant)")
    public ResponseEntity<FieldDetailDto> getFieldDetails(
            @PathVariable Long id,
            @CurrentTenant Tenant tenant) {
        return ResponseEntity.ok(fieldService.getFieldDetail(id, tenant));
    }

}

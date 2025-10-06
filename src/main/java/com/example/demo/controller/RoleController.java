package com.example.demo.controller;

import com.example.demo.dto.RoleCreateDto;
import com.example.demo.dto.RoleUpdateDto;
import com.example.demo.dto.RoleViewDto;
import com.example.demo.entity.Tenant;
import com.example.demo.security.CurrentTenant;
import com.example.demo.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<List<RoleViewDto>> getAllTenantRoles(@CurrentTenant Tenant tenant) {
        List<RoleViewDto> roles = roleService.getAllTenantRoles(tenant);
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<RoleViewDto> getTenantRoleById(@PathVariable Long id, @CurrentTenant Tenant tenant) {
        try {
            RoleViewDto role = roleService.getTenantRoleById(id, tenant);
            return ResponseEntity.ok(role);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<RoleViewDto> createTenantRole(
            @Valid @RequestBody RoleCreateDto createDto, 
            @CurrentTenant Tenant tenant) {
        try {
            RoleViewDto createdRole = roleService.createTenantRole(createDto, tenant);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdRole);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<RoleViewDto> updateTenantRole(
            @PathVariable Long id, 
            @Valid @RequestBody RoleUpdateDto updateDto, 
            @CurrentTenant Tenant tenant) {
        try {
            RoleViewDto updatedRole = roleService.updateTenantRole(id, updateDto, tenant);
            return ResponseEntity.ok(updatedRole);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<Void> deleteTenantRole(@PathVariable Long id, @CurrentTenant Tenant tenant) {
        try {
            roleService.deleteTenantRole(id, tenant);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

}

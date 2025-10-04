package com.example.demo.controller;

import com.example.demo.dto.RoleCreateDto;
import com.example.demo.dto.RoleUpdateDto;
import com.example.demo.dto.RoleViewDto;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.enums.RoleName;
import com.example.demo.enums.ScopeType;
import com.example.demo.repository.RoleRepository;
import com.example.demo.security.CurrentTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleRepository roleRepository;

    @GetMapping
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<List<RoleViewDto>> getAllRoles(@CurrentTenant Tenant tenant) {
        List<Role> roles = roleRepository.findByTenantId(tenant.getId());
        List<RoleViewDto> roleDtos = roles.stream()
                .filter(role -> !role.isDefaultRole()) // Escludi i ruoli default
                .map(this::toViewDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(roleDtos);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<RoleViewDto> getRoleById(@PathVariable Long id, @CurrentTenant Tenant tenant) {
        return roleRepository.findById(id)
                .filter(role -> role.getTenant().getId().equals(tenant.getId()))
                .map(role -> ResponseEntity.ok(toViewDto(role)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<RoleViewDto> createRole(@RequestBody RoleCreateDto createDto, @CurrentTenant Tenant tenant) {
        Role role = new Role();
        role.setName(RoleName.valueOf(createDto.getName()));
        role.setScope(ScopeType.valueOf(createDto.getScope()));
        role.setDefaultRole(createDto.isDefaultRole());
        role.setTenant(tenant);
        
        Role savedRole = roleRepository.save(role);
        return ResponseEntity.status(HttpStatus.CREATED).body(toViewDto(savedRole));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<RoleViewDto> updateRole(@PathVariable Long id, @RequestBody RoleUpdateDto updateDto, @CurrentTenant Tenant tenant) {
        return roleRepository.findById(id)
                .filter(role -> role.getTenant().getId().equals(tenant.getId()))
                .map(role -> {
                    role.setName(RoleName.valueOf(updateDto.getName()));
                    role.setScope(ScopeType.valueOf(updateDto.getScope()));
                    role.setDefaultRole(updateDto.isDefaultRole());
                    
                    Role savedRole = roleRepository.save(role);
                    return ResponseEntity.ok(toViewDto(savedRole));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityService.hasAccessToGlobals(principal, #tenant)")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id, @CurrentTenant Tenant tenant) {
        return roleRepository.findById(id)
                .filter(role -> role.getTenant().getId().equals(tenant.getId()))
                .map(role -> {
                    roleRepository.delete(role);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private RoleViewDto toViewDto(Role role) {
        return new RoleViewDto(
                role.getId(),
                role.getName().toString(),
                role.getScope().toString(),
                role.isDefaultRole()
        );
    }

    // DTOs
    public static class RoleCreateDto {
        private String name;
        private String scope;
        private boolean defaultRole;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public boolean isDefaultRole() { return defaultRole; }
        public void setDefaultRole(boolean defaultRole) { this.defaultRole = defaultRole; }
    }

    public static class RoleUpdateDto {
        private String name;
        private String scope;
        private boolean defaultRole;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public boolean isDefaultRole() { return defaultRole; }
        public void setDefaultRole(boolean defaultRole) { this.defaultRole = defaultRole; }
    }

    public static class RoleViewDto {
        private Long id;
        private String name;
        private String scope;
        private boolean defaultRole;

        public RoleViewDto(Long id, String name, String scope, boolean defaultRole) {
            this.id = id;
            this.name = name;
            this.scope = scope;
            this.defaultRole = defaultRole;
        }

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public boolean isDefaultRole() { return defaultRole; }
        public void setDefaultRole(boolean defaultRole) { this.defaultRole = defaultRole; }
    }
}

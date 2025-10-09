package com.example.demo.controller;

import com.example.demo.dto.TenantDTO;
import com.example.demo.entity.Tenant;
import com.example.demo.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    public Tenant createTenant(@RequestBody Tenant tenant) {
        return tenantService.createTenant(tenant);
    }

    @GetMapping
    public ResponseEntity<List<TenantDTO>> getUserTenants(Authentication authentication) {
        String username = authentication.getName();
        List<TenantDTO> tenants = tenantService.getTenantsByUser(username);
        return ResponseEntity.ok(tenants);
    }
}
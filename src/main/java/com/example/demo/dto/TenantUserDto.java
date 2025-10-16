package com.example.demo.dto;

import java.util.List;

/**
 * DTO per rappresentare un utente con i suoi ruoli in una tenant
 */
public record TenantUserDto(
        Long id,
        String username,
        String fullName,
        List<String> roles  // Lista dei nomi dei ruoli (es: ["ADMIN"], ["USER"])
) {}





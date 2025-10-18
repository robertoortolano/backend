package com.example.demo.dto;

/**
 * DTO per rappresentare un membro di un progetto con il suo ruolo
 */
public record ProjectMemberDto(
        Long userId,
        String username,
        String fullName,
        String roleName,  // ADMIN, USER, ecc.
        boolean isTenantAdmin  // true se l'utente è TENANT_ADMIN (ruolo non modificabile dal progetto)
) {}


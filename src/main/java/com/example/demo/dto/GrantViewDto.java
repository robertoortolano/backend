package com.example.demo.dto;

import java.util.Set;

/**
 * DTO per visualizzare un Grant (Permission assignment).
 * Include info dal Role custom associato (senza scope).
 * 
 * Nota: Grant è usato SOLO per Permission granulari, NON per autenticazione ADMIN/USER.
 */
public record GrantViewDto (
    Long id,
    Long roleId,              // ID del Role custom associato
    String roleName,          // Nome del Role custom (es: "Developer", "QA")
    boolean defaultRole,      // Se il Role è default
    Set<UserResponseDto> users,
    Set<GroupViewDto> groups,
    Set<UserResponseDto> negatedUsers,
    Set<GroupViewDto> negatedGroups
) {}

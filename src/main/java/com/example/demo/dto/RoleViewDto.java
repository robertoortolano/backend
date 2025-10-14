package com.example.demo.dto;

/**
 * DTO per visualizzare i ruoli custom.
 * Nota: Role custom non hanno scope (sono sempre TENANT implicitamente).
 * I ruoli di sistema ADMIN/USER sono gestiti tramite UserRole.
 */
public record RoleViewDto (
    Long id,
    String name,
    String description,
    boolean defaultRole
) {}

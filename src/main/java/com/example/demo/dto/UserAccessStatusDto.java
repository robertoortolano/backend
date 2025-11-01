package com.example.demo.dto;

import java.util.List;

/**
 * DTO per rappresentare lo stato di accesso di un utente a una tenant
 */
public record UserAccessStatusDto(
        Long userId,
        String username,
        String fullName,
        boolean hasAccess,
        List<String> roles  // Ruoli dell'utente nella tenant (vuoto se hasAccess = false)
) {}

































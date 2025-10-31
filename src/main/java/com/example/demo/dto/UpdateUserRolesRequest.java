package com.example.demo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request DTO per aggiornare i ruoli di un utente esistente nella tenant.
 * Supporta assegnazione multipla di ruoli.
 */
public record UpdateUserRolesRequest(
        @NotNull(message = "User ID is required")
        Long userId,
        
        @NotEmpty(message = "At least one role must be assigned")
        List<String> roleNames  // Es: ["ADMIN", "USER"] o solo ["USER"]
) {}































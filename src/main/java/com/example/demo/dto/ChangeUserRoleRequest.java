package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO per cambiare il ruolo di un utente esistente nella tenant
 */
public record ChangeUserRoleRequest(
        @NotNull(message = "User ID is required")
        Long userId,
        
        @Pattern(regexp = "ADMIN|USER", message = "Role name must be either ADMIN or USER")
        String newRoleName
) {}






























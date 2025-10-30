package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO per assegnare un ruolo (ADMIN o USER) a un utente in una tenant
 */
public record AssignRoleRequest(
        @NotBlank(message = "Username (email) is required")
        @Email(message = "Username must be a valid email")
        String username,
        
        @NotBlank(message = "Role name is required")
        @Pattern(regexp = "ADMIN|USER", message = "Role name must be either ADMIN or USER")
        String roleName
) {}






























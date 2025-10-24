package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO per revocare accesso di un utente da una tenant
 */
public record RevokeUserAccessRequest(
        @NotNull(message = "User ID is required")
        Long userId
) {}
















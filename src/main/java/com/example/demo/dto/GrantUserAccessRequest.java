package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO per concedere accesso USER a un utente in una tenant
 */
public record GrantUserAccessRequest(
        @NotBlank(message = "Username (email) is required")
        @Email(message = "Username must be a valid email")
        String username
) {}




























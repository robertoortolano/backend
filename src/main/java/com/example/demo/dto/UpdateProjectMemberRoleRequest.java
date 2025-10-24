package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request per aggiornare il ruolo di un membro del progetto
 */
public record UpdateProjectMemberRoleRequest(
        @NotNull String roleName  // ADMIN, USER, ecc.
) {}










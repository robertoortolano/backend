package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request per aggiungere un membro a un progetto
 */
public record AddProjectMemberRequest(
        @NotNull Long userId,
        @NotNull String roleName  // ADMIN, USER, ecc.
) {}










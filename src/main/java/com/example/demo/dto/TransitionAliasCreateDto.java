package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransitionAliasCreateDto (
    @NotBlank(message = "Transition alias name required")
    String alias,
    @NotNull(message = "Project id required")
    Long projectId,
    @NotNull(message = "Transition id required")
    Long transitionId

) {}

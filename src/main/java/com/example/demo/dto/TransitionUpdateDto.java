package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

public record TransitionUpdateDto (

    @NotNull
    Long id,
    String tempId,
    String name,
    @NotNull(message = "Transition from Status required")
    Long fromStatusId,
    @NotNull(message = "Transition to Status required")
    Long toStatusId
) {}

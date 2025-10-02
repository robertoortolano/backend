package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

public record TransitionCreateDto (

    String tempId,
    String name,
    @NotNull(message = "Transition from Status required")
    Long fromStatusId,
    @NotNull(message = "Transition to Status required")
    Long toStatusId

) {}

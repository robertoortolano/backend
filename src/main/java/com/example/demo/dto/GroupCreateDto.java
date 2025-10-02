package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record GroupCreateDto (
    @NotBlank(message = "Group name required")
    String name,
    String description,
    @NotNull(message = "User ids required")
    @NotEmpty(message = "User ids required")
    Set<Long> userIds

) {}

package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record GroupUpdateDto (
    @NotBlank(message = "Group name required")
    String name,
    String description,
    Set<Long> userIds
) {}






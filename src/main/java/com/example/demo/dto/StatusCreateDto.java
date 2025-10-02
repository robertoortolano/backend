package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record StatusCreateDto (
    @NotBlank(message = "Status name required")
    String name

) {}

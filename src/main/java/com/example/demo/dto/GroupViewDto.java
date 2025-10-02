package com.example.demo.dto;

import java.util.Set;

public record GroupViewDto (
    Long id,
    String name,
    String description,
    Set<UserResponseDto> users
) {}

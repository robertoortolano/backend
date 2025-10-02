package com.example.demo.dto;

public record UserResponseDto (
    Long id,
    String username,
    String passwordHash,
    String fullName
) {}

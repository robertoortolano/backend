package com.example.demo.dto;

public record AssignUserRequest (
    String username,
    Long tenantId,
    String role
) {}


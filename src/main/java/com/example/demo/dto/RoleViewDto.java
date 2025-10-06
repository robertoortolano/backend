package com.example.demo.dto;

import com.example.demo.enums.ScopeType;

public record RoleViewDto (
    Long id,
    String name,
    String description,
    ScopeType scope,
    boolean defaultRole
) {}

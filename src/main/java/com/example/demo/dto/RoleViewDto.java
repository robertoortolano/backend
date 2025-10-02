package com.example.demo.dto;

import com.example.demo.enums.RoleName;
import com.example.demo.enums.ScopeType;

public record RoleViewDto (
    Long id,
    RoleName name,
    ScopeType scope,
    boolean defaultRole
) {}

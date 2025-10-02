package com.example.demo.dto;

import com.example.demo.enums.RoleName;
import com.example.demo.enums.ScopeType;

import java.util.Set;

public record GrantViewDto (
    Long id,
    Long roleId,
    RoleName name,
    ScopeType scope,
    boolean defaultRole,
    Set<UserResponseDto> users,
    Set<GroupViewDto> groups,
    Set<UserResponseDto> negatedUsers,
    Set<GroupViewDto> negatedGroups
) {}

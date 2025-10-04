package com.example.demo.dto;

import com.example.demo.enums.RoleName;
import com.example.demo.enums.ScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateDto (
        @NotNull(message = "Role ID required")
        Long id,
        @NotBlank(message = "Role name required")
        RoleName name,
        @NotNull(message = "Role scope required")
        ScopeType scope

) {}

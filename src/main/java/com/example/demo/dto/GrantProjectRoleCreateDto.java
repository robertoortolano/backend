package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

public record GrantProjectRoleCreateDto (

    @NotNull(message = "Role id required")
    Long roleId,
    @NotNull(message = "Project id required")
    Long projectId,
    @NotNull(message = "Grant id required")
    Long grantId

) {}

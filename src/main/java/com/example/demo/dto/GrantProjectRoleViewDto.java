package com.example.demo.dto;

public record GrantProjectRoleViewDto (
    Long id,
    RoleViewDto role,
    ProjectSummaryDto project,
    GrantViewDto grant
) {}

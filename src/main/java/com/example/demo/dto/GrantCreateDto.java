package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GrantCreateDto (

    @NotNull(message = "Role id required")
    Long roleId,
    List<Long> userIds,
    List<Long> groups,
    List<Long> negatedUsers,
    List<Long> negatedGroups

) {}

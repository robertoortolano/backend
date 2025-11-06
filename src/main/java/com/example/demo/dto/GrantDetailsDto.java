package com.example.demo.dto;

import java.util.List;

/**
 * DTO per i dettagli di un Grant, inclusi utenti e gruppi.
 */
public record GrantDetailsDto (
    Long id,
    List<UserSimpleDto> users,
    List<GroupSimpleDto> groups,
    List<UserSimpleDto> negatedUsers,
    List<GroupSimpleDto> negatedGroups
) {}









package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO per creare un Grant e assegnarlo direttamente a un ItemTypeSetRole.
 * Il Grant viene creato al momento dell'assegnazione.
 */
public record ItemTypeSetRoleGrantCreateDto (
    
    @NotNull(message = "ItemTypeSetRole id required")
    Long itemTypeSetRoleId,
    
    List<Long> userIds,
    List<Long> groupIds,
    List<Long> negatedUserIds,
    List<Long> negatedGroupIds
    
) {}



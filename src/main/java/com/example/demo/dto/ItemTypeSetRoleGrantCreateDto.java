package com.example.demo.dto;

import com.example.demo.enums.ItemTypeSetRoleType;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO per creare un Grant e assegnarlo direttamente a un ItemTypeSetRole.
 * Il Grant viene creato al momento dell'assegnazione.
 * Se itemTypeSetRoleId non esiste, verrà creato automaticamente usando le informazioni opzionali fornite.
 */
public record ItemTypeSetRoleGrantCreateDto (
    
    @NotNull(message = "ItemTypeSetRole id required")
    Long itemTypeSetRoleId,
    
    List<Long> userIds,
    List<Long> groupIds,
    List<Long> negatedUserIds,
    List<Long> negatedGroupIds,
    
    // Informazioni opzionali per creare l'ItemTypeSetRole se non esiste
    Long itemTypeSetId,
    ItemTypeSetRoleType permissionType,
    Long itemTypeId,
    Long workflowId,
    Long workflowStatusId,
    Long fieldConfigurationId,
    Long transitionId
    
) {}



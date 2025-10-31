package com.example.demo.dto;

import com.example.demo.enums.ItemTypeCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record ItemTypeConfigurationCreateDto (
    Long id, // ID opzionale (usato per aggiornamenti)
    @NotNull(message = "Item Type id required")
    Long itemTypeId,
    @NotNull(message = "Item Type Category required")
    ItemTypeCategory category,

    Set<Integer> workerIds,
    @NotNull(message = "Workflow id required")
    Long workflowId,
    @NotNull(message = "Field Set id required")
    Long fieldSetId

) {}

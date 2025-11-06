package com.example.demo.dto;

import com.example.demo.enums.ItemTypeCategory;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record ItemTypeConfigurationUpdateDto (

        Long id,
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
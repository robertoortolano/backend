package com.example.demo.dto;

import java.util.List;

public record WorkflowDetailDto(
        Long id,
        String name,
        boolean defaultWorkflow,
        List<ItemTypeConfigurationViewDto> usedInItemTypeConfigurations
) {}
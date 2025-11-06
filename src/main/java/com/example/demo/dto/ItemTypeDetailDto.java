package com.example.demo.dto;

import java.util.List;

public record ItemTypeDetailDto(
        Long id,
        String name,
        String description,
        boolean defaultItemType,
        List<ItemTypeConfigurationViewDto> itemTypeConfigurations
) {}
























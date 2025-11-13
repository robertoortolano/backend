package com.example.demo.dto;

import java.util.Set;

public record ItemTypeSetUpdateDto (

        Long id,
        String name,
        Set<ItemTypeConfigurationCreateDto> itemTypeConfigurations,
        Boolean forceRemoval

) {}

package com.example.demo.dto;

import java.util.Set;

public record ItemTypeSetCreateDto (

    String name,
    Set<ItemTypeConfigurationCreateDto> itemTypeConfigurations

) {}

package com.example.demo.dto;

public record ProjectViewDto (
        Long id,
        String key,
        String name,
        String description,
        ItemTypeSetViewDto itemTypeSet
) {}

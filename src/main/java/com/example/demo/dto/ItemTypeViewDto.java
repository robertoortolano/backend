package com.example.demo.dto;

public record ItemTypeViewDto (
    Long id,
    String name,
    String description,
    boolean defaultItemType
) {}

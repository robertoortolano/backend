package com.example.demo.dto;

public record ProjectUpdateDto (
    String key,
    String name,
    String description,
    Long itemTypeSetId
) {}

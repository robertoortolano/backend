package com.example.demo.dto;


public record FieldOptionViewDto (
    Long id,
    String label,
    String value,
    boolean enabled,
    int orderIndex
) {}


package com.example.demo.dto;

public record FieldOptionUpdateDto (

    Long id,
    String label,
    String value,
    boolean enabled,
    int orderIndex

) {}
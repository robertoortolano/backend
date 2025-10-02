package com.example.demo.dto;

public record ProjectSummaryDto (
    Long id,
    String projectKey,
    String name,
    String description
) {}

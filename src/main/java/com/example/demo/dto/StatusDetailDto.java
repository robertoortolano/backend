package com.example.demo.dto;

import java.util.List;

public record StatusDetailDto (
        Long id,
        String name,
        boolean defaultStatus,
        List<WorkflowSimpleDto> workflows
) {}

package com.example.demo.dto;

public record WorkflowStatusViewDto (
    Long id,
    Long workflowId,
    String workflowName,
    StatusViewDto status,
    boolean initial,
    String statusCategory
) {}


package com.example.demo.dto;

import java.util.Set;

public record WorkflowStatusViewDto (
    Long id,
    Long workflowId,
    String workflowName,
    StatusViewDto status,
    boolean initial,
    String statusCategory
) {}


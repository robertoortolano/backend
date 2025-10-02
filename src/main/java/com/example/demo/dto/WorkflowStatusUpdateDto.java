package com.example.demo.dto;

import com.example.demo.enums.StatusCategory;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record WorkflowStatusUpdateDto (
        @NotNull
        Long id,
        @NotNull(message = "Status id required")
        Long statusId,
        @NotNull(message = "Status Category required")
        StatusCategory statusCategory,
        boolean isInitial,
        List<TransitionUpdateDto> outgoingTransitions
) {}

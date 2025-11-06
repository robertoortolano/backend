package com.example.demo.dto;

import com.example.demo.metadata.WorkflowEdgeDto;
import com.example.demo.metadata.WorkflowNodeDto;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record WorkflowUpdateDto(
        Long id,                                 // id del workflow da aggiornare
        @NotBlank(message = "Workflow name required")
        String name,                             // nuovo nome
        Long initialStatusId,                    // id dello stato iniziale
        List<WorkflowStatusUpdateDto> workflowStatuses, // stati + transizion
        List<WorkflowNodeDto> workflowNodes,     // metadati di posizionamento
        List<TransitionUpdateDto> transitions,           // transizioni
        List<WorkflowEdgeDto> workflowEdges
) {}

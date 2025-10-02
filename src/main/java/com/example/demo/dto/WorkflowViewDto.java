package com.example.demo.dto;



import com.example.demo.enums.ScopeType;
import com.example.demo.metadata.WorkflowEdgeDto;
import com.example.demo.metadata.WorkflowNodeDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class WorkflowViewDto {
    private Long id;
    private String name;
    private StatusViewDto initialStatus;
    private ScopeType scope;
    private boolean defaultWorkflow;
    private Set<WorkflowStatusViewDto> statuses;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<WorkflowNodeDto> workflowNodes;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<WorkflowEdgeDto> workflowEdges;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<TransitionViewDto> transitions;

}

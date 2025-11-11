package com.example.demo.service.workflow;

import com.example.demo.dto.WorkflowUpdateDto;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Transition;
import com.example.demo.entity.Workflow;
import com.example.demo.metadata.WorkflowEdge;
import com.example.demo.metadata.WorkflowEdgeDto;
import com.example.demo.metadata.WorkflowEdgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowEdgeManager {

    private final WorkflowEdgeRepository workflowEdgeRepository;

    public void synchronizeEdges(
            Workflow workflow,
            WorkflowUpdateDto dto,
            Tenant tenant,
            WorkflowTransitionSyncResult transitionContext
    ) {
        Map<Long, WorkflowEdge> existingEdges = workflowEdgeRepository
                .findByWorkflowIdAndTenant(workflow.getId(), tenant)
                .stream()
                .collect(HashMap::new, (map, edge) -> map.put(edge.getId(), edge), Map::putAll);

        for (WorkflowEdgeDto edgeDto : dto.workflowEdges()) {
            WorkflowEdge edge = (edgeDto.id() != null && existingEdges.containsKey(edgeDto.id()))
                    ? existingEdges.remove(edgeDto.id())
                    : new WorkflowEdge();

            edge.setWorkflow(workflow);
            edge.setTenant(tenant);
            edge.setSourceId(edgeDto.sourceId());
            edge.setTargetId(edgeDto.targetId());
            edge.setSourcePosition(edgeDto.sourcePosition());
            edge.setTargetPosition(edgeDto.targetPosition());

            Transition transition = resolveTransition(edgeDto, transitionContext);
            if (transition != null) {
                edge.setTransition(transition);
                transition.setEdge(edge);
            }

            workflowEdgeRepository.save(edge);
        }

        existingEdges.values().forEach(workflowEdgeRepository::delete);
    }

    private Transition resolveTransition(WorkflowEdgeDto edgeDto, WorkflowTransitionSyncResult transitionContext) {
        Transition transition = null;
        if (edgeDto.transitionId() != null) {
            transition = transitionContext.getTransitionById().get(edgeDto.transitionId());
        } else if (edgeDto.transitionTempId() != null) {
            transition = transitionContext.getTransitionByTempId().get(edgeDto.transitionTempId());
        }
        return transition;
    }
}


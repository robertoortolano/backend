package com.example.demo.service.workflow;

import com.example.demo.dto.TransitionUpdateDto;
import com.example.demo.dto.WorkflowUpdateDto;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Transition;
import com.example.demo.entity.Workflow;
import com.example.demo.entity.WorkflowStatus;
import com.example.demo.repository.TransitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowTransitionSynchronizer {

    private final TransitionRepository transitionRepository;

    public WorkflowTransitionSyncResult synchronizeTransitions(
            Workflow workflow,
            WorkflowUpdateDto dto,
            Tenant tenant,
            Map<Long, WorkflowStatus> updatedStatusMap
    ) {
        Map<Long, Transition> existingTransitions = workflow.getTransitions().stream()
                .filter(transition -> transition.getId() != null)
                .collect(HashMap::new, (map, transition) -> map.put(transition.getId(), transition), Map::putAll);

        Set<Long> existingTransitionIdsSnapshot = new HashSet<>(existingTransitions.keySet());

        Map<Long, Transition> transitionMapById = new HashMap<>();
        Map<String, Transition> transitionMapByTempId = new HashMap<>();

        for (TransitionUpdateDto transitionDto : dto.transitions()) {
            Transition transition = null;

            if (transitionDto.id() != null) {
                transition = existingTransitions.remove(transitionDto.id());
            }
            if (transition == null) {
                transition = new Transition();
            }

            transition.setWorkflow(workflow);
            transition.setFromStatus(updatedStatusMap.get(transitionDto.fromStatusId()));
            transition.setToStatus(updatedStatusMap.get(transitionDto.toStatusId()));
            transition.setName(transitionDto.name() != null ? transitionDto.name() : "");

            transition = transitionRepository.save(transition);

            if (transition.getId() != null) {
                transitionMapById.put(transition.getId(), transition);
            }
            if (transitionDto.tempId() != null) {
                transitionMapByTempId.put(transitionDto.tempId(), transition);
            }
        }

        Collection<Transition> obsoleteTransitions = new ArrayList<>(existingTransitions.values());

        workflow.getTransitions().clear();
        workflow.getTransitions().addAll(transitionMapById.values());

        return new WorkflowTransitionSyncResult(
                transitionMapById,
                transitionMapByTempId,
                existingTransitionIdsSnapshot,
                obsoleteTransitions
        );
    }
}


package com.example.demo.service.workflow;

import com.example.demo.entity.Transition;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class WorkflowTransitionSyncResult {

    private final Map<Long, Transition> transitionById;
    private final Map<String, Transition> transitionByTempId;
    private final Set<Long> existingTransitionIdsBeforeUpdate;
    private final Collection<Transition> obsoleteTransitions;

    public WorkflowTransitionSyncResult(
            Map<Long, Transition> transitionById,
            Map<String, Transition> transitionByTempId,
            Set<Long> existingTransitionIdsBeforeUpdate,
            Collection<Transition> obsoleteTransitions
    ) {
        this.transitionById = transitionById;
        this.transitionByTempId = transitionByTempId;
        this.existingTransitionIdsBeforeUpdate = existingTransitionIdsBeforeUpdate;
        this.obsoleteTransitions = obsoleteTransitions;
    }

    public Map<Long, Transition> getTransitionById() {
        return Collections.unmodifiableMap(transitionById);
    }

    public Map<String, Transition> getTransitionByTempId() {
        return Collections.unmodifiableMap(transitionByTempId);
    }

    public Set<Long> getExistingTransitionIdsBeforeUpdate() {
        return Collections.unmodifiableSet(existingTransitionIdsBeforeUpdate);
    }

    public Collection<Transition> getObsoleteTransitions() {
        return Collections.unmodifiableCollection(obsoleteTransitions);
    }
}








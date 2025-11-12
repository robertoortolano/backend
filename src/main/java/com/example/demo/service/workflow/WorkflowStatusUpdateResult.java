package com.example.demo.service.workflow;

import com.example.demo.entity.WorkflowStatus;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class WorkflowStatusUpdateResult {

    private final Map<Long, WorkflowStatus> statusByStatusId;
    private final Set<Long> newWorkflowStatusIds;

    public WorkflowStatusUpdateResult(
            Map<Long, WorkflowStatus> statusByStatusId,
            Set<Long> newWorkflowStatusIds
    ) {
        this.statusByStatusId = statusByStatusId;
        this.newWorkflowStatusIds = newWorkflowStatusIds;
    }

    public Map<Long, WorkflowStatus> getStatusByStatusId() {
        return Collections.unmodifiableMap(statusByStatusId);
    }

    public Set<Long> getNewWorkflowStatusIds() {
        return Collections.unmodifiableSet(newWorkflowStatusIds);
    }
}





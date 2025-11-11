package com.example.demo.service.workflow;

import com.example.demo.dto.WorkflowUpdateDto;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Workflow;
import com.example.demo.entity.WorkflowStatus;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.WorkflowStatusRepository;
import com.example.demo.metadata.WorkflowNode;
import com.example.demo.metadata.WorkflowNodeDto;
import com.example.demo.metadata.WorkflowNodeRepository;
import com.example.demo.service.StatusLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowStatusUpdater {

    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final StatusLookup statusLookup;

    public WorkflowStatusUpdateResult updateWorkflowStatuses(Workflow workflow, WorkflowUpdateDto dto, Tenant tenant) {
        Map<Long, WorkflowStatus> updatedStatusMap = new HashMap<>();
        Set<Long> newWorkflowStatusIds = new HashSet<>();
        synchronizeStatuses(workflow, dto, tenant, updatedStatusMap, newWorkflowStatusIds);
        synchronizeNodes(workflow, dto, tenant, updatedStatusMap);
        updateInitialStatus(workflow, dto, updatedStatusMap);

        return new WorkflowStatusUpdateResult(updatedStatusMap, newWorkflowStatusIds);
    }

    private void synchronizeStatuses(
            Workflow workflow,
            WorkflowUpdateDto dto,
            Tenant tenant,
            Map<Long, WorkflowStatus> updatedStatusMap,
            Set<Long> newWorkflowStatusIds
    ) {
        Map<Long, WorkflowStatus> existingStatusMap = workflow.getStatuses().stream()
                .collect(Collectors.toMap(ws -> ws.getStatus().getId(), ws -> ws));

        for (var wsDto : dto.workflowStatuses()) {
            WorkflowStatus workflowStatus = existingStatusMap.remove(wsDto.statusId());
            boolean isNew = false;
            if (workflowStatus == null) {
                workflowStatus = new WorkflowStatus();
                workflowStatus.setWorkflow(workflow);
                workflowStatus.setStatus(statusLookup.getById(tenant, wsDto.statusId()));
                isNew = true;
            }

            workflowStatus.setStatusCategory(wsDto.statusCategory());
            workflowStatus.setInitial(wsDto.isInitial());
            workflowStatus = workflowStatusRepository.save(workflowStatus);

            updatedStatusMap.put(wsDto.statusId(), workflowStatus);
            if (isNew && workflowStatus.getId() != null) {
                newWorkflowStatusIds.add(workflowStatus.getId());
            }
        }

        existingStatusMap.values().forEach(workflowStatusRepository::delete);

        workflow.getStatuses().clear();
        workflow.getStatuses().addAll(updatedStatusMap.values());
    }

    private void synchronizeNodes(
            Workflow workflow,
            WorkflowUpdateDto dto,
            Tenant tenant,
            Map<Long, WorkflowStatus> updatedStatusMap
    ) {
        Map<Long, WorkflowNode> existingNodes = workflow.getStatuses().stream()
                .map(WorkflowStatus::getNode)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(node -> node.getWorkflowStatus().getStatus().getId(), node -> node));

        for (WorkflowNodeDto nodeDto : dto.workflowNodes()) {
            WorkflowStatus workflowStatus = updatedStatusMap.get(nodeDto.statusId());
            if (workflowStatus == null) {
                throw new ApiException("WorkflowNode refers to missing statusId: " + nodeDto.statusId());
            }

            WorkflowNode node = existingNodes.remove(nodeDto.statusId());
            if (node == null) {
                node = new WorkflowNode();
                node.setWorkflowStatus(workflowStatus);
                node.setWorkflow(workflow);
            }

            node.setTenant(tenant);
            node.setPositionX(nodeDto.positionX());
            node.setPositionY(nodeDto.positionY());
            workflowNodeRepository.save(node);

            workflowStatus.setNode(node);
        }

        existingNodes.values().forEach(workflowNodeRepository::delete);
    }

    private void updateInitialStatus(
            Workflow workflow,
            WorkflowUpdateDto dto,
            Map<Long, WorkflowStatus> updatedStatusMap
    ) {
        if (dto.initialStatusId() == null) {
            return;
        }

        WorkflowStatus initialWorkflowStatus = updatedStatusMap.get(dto.initialStatusId());
        if (initialWorkflowStatus == null) {
            throw new ApiException("Invalid initialStatusId: " + dto.initialStatusId());
        }
        workflow.setInitialStatus(initialWorkflowStatus.getStatus());
    }
}


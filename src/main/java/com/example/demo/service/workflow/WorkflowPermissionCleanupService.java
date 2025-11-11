package com.example.demo.service.workflow;

import com.example.demo.entity.ExecutorPermission;
import com.example.demo.entity.Field;
import com.example.demo.entity.FieldStatusPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Transition;
import com.example.demo.entity.Workflow;
import com.example.demo.entity.WorkflowStatus;
import com.example.demo.entity.StatusOwnerPermission;
import com.example.demo.entity.Tenant;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ExecutorPermissionRepository;
import com.example.demo.repository.FieldStatusPermissionRepository;
import com.example.demo.repository.StatusOwnerPermissionRepository;
import com.example.demo.repository.TransitionRepository;
import com.example.demo.repository.WorkflowStatusRepository;
import com.example.demo.service.FieldLookup;
import com.example.demo.service.ItemTypeSetLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowPermissionCleanupService {

    private final ItemTypeSetLookup itemTypeSetLookup;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final FieldLookup fieldLookup;
    private final TransitionRepository transitionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;

    public void handleNewWorkflowStatuses(Tenant tenant, Workflow workflow, Set<Long> newWorkflowStatusIds) {
        if (newWorkflowStatusIds == null || newWorkflowStatusIds.isEmpty()) {
            return;
        }

        List<ItemTypeSet> affectedItemTypeSets = findItemTypeSetsUsingWorkflow(workflow.getId(), tenant);

        for (ItemTypeSet itemTypeSet : affectedItemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                if (config.getWorkflow().getId().equals(workflow.getId())) {
                    createPermissionsForNewWorkflowStatuses(config, newWorkflowStatusIds, tenant);
                }
            }
        }
    }

    public void handleNewTransitions(
            Tenant tenant,
            Workflow workflow,
            Set<Long> existingTransitionIdsBeforeUpdate
    ) {
        Set<String> existingTransitionKeys = existingTransitionIdsBeforeUpdate.stream()
                .map(id -> transitionRepository.findByTransitionIdAndTenant(id, tenant).orElse(null))
                .filter(Objects::nonNull)
                .filter(transition -> transition.getFromStatus() != null && transition.getToStatus() != null)
                .map(transition -> transition.getFromStatus().getStatus().getId() + "->" + transition.getToStatus().getStatus().getId())
                .collect(Collectors.toSet());

        Set<Long> newTransitionIds = workflow.getTransitions().stream()
                .filter(t -> t.getId() != null)
                .filter(t -> t.getFromStatus() != null && t.getToStatus() != null)
                .filter(t -> {
                    String key = t.getFromStatus().getStatus().getId() + "->" + t.getToStatus().getStatus().getId();
                    return !existingTransitionKeys.contains(key);
                })
                .map(Transition::getId)
                .collect(Collectors.toCollection(HashSet::new));

        if (newTransitionIds.isEmpty()) {
            return;
        }

        List<ItemTypeSet> affectedItemTypeSets = findItemTypeSetsUsingWorkflow(workflow.getId(), tenant);

        for (ItemTypeSet itemTypeSet : affectedItemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                if (config.getWorkflow().getId().equals(workflow.getId())) {
                    createPermissionsForNewTransitions(config, newTransitionIds, tenant);
                }
            }
        }
    }

    public void cleanupObsoleteTransitions(Tenant tenant, Collection<Transition> obsoleteTransitions) {
        if (obsoleteTransitions == null || obsoleteTransitions.isEmpty()) {
            return;
        }

        for (Transition obsolete : obsoleteTransitions) {
            if (obsolete.getId() != null) {
                List<ExecutorPermission> permissions = executorPermissionRepository
                        .findByTransitionIdAndTenant(obsolete.getId(), tenant);
                for (ExecutorPermission permission : permissions) {
                    executorPermissionRepository.delete(permission);
                }
            }
            transitionRepository.delete(obsolete);
        }
    }

    private List<ItemTypeSet> findItemTypeSetsUsingWorkflow(Long workflowId, Tenant tenant) {
        return itemTypeSetLookup.findByWorkflowId(workflowId, tenant);
    }

    private void createPermissionsForNewWorkflowStatuses(
            ItemTypeConfiguration config,
            Set<Long> newWorkflowStatusIds,
            Tenant tenant
    ) {
        for (Long workflowStatusId : newWorkflowStatusIds) {
            createStatusOwnerPermission(config, workflowStatusId, tenant);
            createFieldStatusPermissionsForNewStatus(config, workflowStatusId, tenant);
        }
    }

    private void createStatusOwnerPermission(ItemTypeConfiguration config, Long workflowStatusId, Tenant tenant) {
        var existingPermission = statusOwnerPermissionRepository
                .findByItemTypeConfigurationAndWorkflowStatusId(config, workflowStatusId);

        if (existingPermission == null) {
            WorkflowStatus workflowStatus = workflowStatusRepository.findByIdAndTenant(workflowStatusId, tenant)
                    .orElseThrow(() -> new ApiException("WorkflowStatus not found: " + workflowStatusId));

            StatusOwnerPermission permission = new StatusOwnerPermission();
            permission.setItemTypeConfiguration(config);
            permission.setWorkflowStatus(workflowStatus);

            statusOwnerPermissionRepository.save(permission);
        }
    }

    private void createFieldStatusPermissionsForNewStatus(
            ItemTypeConfiguration config,
            Long workflowStatusId,
            Tenant tenant
    ) {
        WorkflowStatus workflowStatus = workflowStatusRepository.findByIdAndTenant(workflowStatusId, tenant)
                .orElseThrow(() -> new ApiException("WorkflowStatus not found: " + workflowStatusId));

        Set<Long> fieldIds = config.getFieldSet().getFieldSetEntries().stream()
                .map(entry -> entry.getFieldConfiguration().getField().getId())
                .collect(Collectors.toSet());

        for (Long fieldId : fieldIds) {
            Field field = fieldLookup.getById(fieldId, config.getTenant());

            createFieldStatusPermission(config, field, workflowStatus, FieldStatusPermission.PermissionType.EDITORS);
            createFieldStatusPermission(config, field, workflowStatus, FieldStatusPermission.PermissionType.VIEWERS);
        }
    }

    private void createFieldStatusPermission(
            ItemTypeConfiguration config,
            Field field,
            WorkflowStatus workflowStatus,
            FieldStatusPermission.PermissionType permissionType
    ) {
        FieldStatusPermission existingPermission = fieldStatusPermissionRepository
                .findByItemTypeConfigurationAndFieldAndWorkflowStatusAndPermissionType(
                        config, field, workflowStatus, permissionType);

        if (existingPermission == null) {
            FieldStatusPermission permission = new FieldStatusPermission();
            permission.setItemTypeConfiguration(config);
            permission.setField(field);
            permission.setWorkflowStatus(workflowStatus);
            permission.setPermissionType(permissionType);

            fieldStatusPermissionRepository.save(permission);
        }
    }

    private void createPermissionsForNewTransitions(
            ItemTypeConfiguration config,
            Set<Long> newTransitionIds,
            Tenant tenant
    ) {
        for (Long transitionId : newTransitionIds) {
            createExecutorPermission(config, transitionId, tenant);
        }
    }

    private void createExecutorPermission(ItemTypeConfiguration config, Long transitionId, Tenant tenant) {
        ExecutorPermission existingPermission = executorPermissionRepository
                .findByItemTypeConfigurationAndTransitionId(config, transitionId);

        if (existingPermission == null) {
            Transition transition = transitionRepository.findByIdAndTenant(transitionId, tenant)
                    .orElseThrow(() -> new ApiException("Transition not found: " + transitionId));

            ExecutorPermission permission = new ExecutorPermission();
            permission.setItemTypeConfiguration(config);
            permission.setTransition(transition);

            executorPermissionRepository.save(permission);
        }
    }
}


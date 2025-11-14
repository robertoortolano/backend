package com.example.demo.service.workflow;

import com.example.demo.entity.ExecutorPermission;
import com.example.demo.entity.Field;
import com.example.demo.entity.FieldStatusPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Project;
import com.example.demo.entity.Transition;
import com.example.demo.entity.Workflow;
import com.example.demo.entity.WorkflowStatus;
import com.example.demo.entity.StatusOwnerPermission;
import com.example.demo.entity.Tenant;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ExecutorPermissionRepository;
import com.example.demo.repository.FieldStatusPermissionRepository;
import com.example.demo.repository.ItemTypeSetRepository;
import com.example.demo.repository.StatusOwnerPermissionRepository;
import com.example.demo.repository.TransitionRepository;
import com.example.demo.repository.WorkflowStatusRepository;
import com.example.demo.service.FieldLookup;
import com.example.demo.service.ItemTypeSetLookup;
import com.example.demo.service.PermissionAssignmentService;
import com.example.demo.service.ProjectPermissionAssignmentService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WorkflowPermissionCleanupService {

    private final ItemTypeSetLookup itemTypeSetLookup;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final FieldLookup fieldLookup;
    private final TransitionRepository transitionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final ItemTypeSetRepository itemTypeSetRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;
    private final EntityManager entityManager;

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

    /**
     * Rimuove le ExecutorPermissions orfane per le Transition rimosse
     */
    public void removeOrphanedExecutorPermissions(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedTransitionIds
    ) {
        // Trova tutti gli ItemTypeSet che usano questo Workflow
        List<ItemTypeSet> affectedItemTypeSets = findItemTypeSetsUsingWorkflow(workflowId, tenant);

        // Rimuovi ExecutorPermissions orfane
        removeOrphanedExecutorPermissions(affectedItemTypeSets, removedTransitionIds, tenant);
    }

    /**
     * Rimuove le ExecutorPermissions orfane per le Transition rimosse
     */
    private void removeOrphanedExecutorPermissions(
            List<ItemTypeSet> itemTypeSets,
            Set<Long> removedTransitionIds,
            Tenant tenant
    ) {
        if (removedTransitionIds == null || removedTransitionIds.isEmpty()) {
            return;
        }

        Set<Long> permissionsToDelete = new HashSet<>();

        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                // Trova e rimuovi le ExecutorPermissions per le Transition rimosse
                List<ExecutorPermission> permissions = executorPermissionRepository
                        .findByItemTypeConfigurationAndTransitionIdIn(config, removedTransitionIds);

                for (ExecutorPermission permission : permissions) {
                    permissionAssignmentService.deleteAssignment("ExecutorPermission", permission.getId(), itemTypeSet.getTenant());

                    if (itemTypeSet.getProject() != null) {
                        projectPermissionAssignmentService.deleteProjectAssignment(
                                "ExecutorPermission",
                                permission.getId(),
                                itemTypeSet.getProject().getId(),
                                itemTypeSet.getTenant()
                        );
                    } else {
                        for (Project project : itemTypeSet.getProjectsAssociation()) {
                            projectPermissionAssignmentService.deleteProjectAssignment(
                                    "ExecutorPermission",
                                    permission.getId(),
                                    project.getId(),
                                    itemTypeSet.getTenant()
                            );
                        }
                    }

                    permissionsToDelete.add(permission.getId());
                }
            }
        }

        if (!permissionsToDelete.isEmpty()) {
            executorPermissionRepository.deleteAllById(permissionsToDelete);
            // Forza un flush per assicurarsi che le eliminazioni siano committate prima di procedere
            executorPermissionRepository.flush();
            permissionsToDelete.clear();
        }

        for (Long transitionId : removedTransitionIds) {
            List<ExecutorPermission> residualPermissions = executorPermissionRepository.findByTransitionIdAndTenant(transitionId, tenant);
            for (ExecutorPermission permission : residualPermissions) {
                permissionAssignmentService.deleteAssignment("ExecutorPermission", permission.getId(), tenant);
                permissionsToDelete.add(permission.getId());
            }
        }

        if (!permissionsToDelete.isEmpty()) {
            executorPermissionRepository.deleteAllById(permissionsToDelete);
            // Forza un flush finale per assicurarsi che tutte le eliminazioni siano committate
            executorPermissionRepository.flush();
            permissionsToDelete.clear();
        }

        List<ExecutorPermission> remaining = executorPermissionRepository.findAllByTransitionIdIn(removedTransitionIds);
        if (!remaining.isEmpty()) {
            log.warn("ExecutorPermissions still present for transitions {} after cleanup. Permission IDs: {}",
                    removedTransitionIds,
                    remaining.stream().map(ExecutorPermission::getId).collect(Collectors.toSet()));
            executorPermissionRepository.deleteAll(remaining);
            executorPermissionRepository.flush();
        }
    }

    /**
     * Rimuove le StatusOwnerPermissions orfane per gli Status rimossi
     */
    public void removeOrphanedStatusOwnerPermissions(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedStatusIds
    ) {
        if (removedStatusIds == null || removedStatusIds.isEmpty()) {
            return;
        }

        // Trova direttamente tutte le StatusOwnerPermission per gli stati rimossi
        // Questo garantisce che troviamo tutte le permission, anche se non sono associate
        // a un ItemTypeSet che usa il workflow
        List<StatusOwnerPermission> allPermissions = statusOwnerPermissionRepository
                .findByWorkflowStatusIdInAndTenant(removedStatusIds, tenant);

        // Raggruppa le permission per ItemTypeConfiguration per gestire le assegnazioni di progetto
        Map<ItemTypeConfiguration, List<StatusOwnerPermission>> permissionsByConfig = allPermissions.stream()
                .collect(Collectors.groupingBy(StatusOwnerPermission::getItemTypeConfiguration));

        for (Map.Entry<ItemTypeConfiguration, List<StatusOwnerPermission>> entry : permissionsByConfig.entrySet()) {
            ItemTypeConfiguration config = entry.getKey();
            List<StatusOwnerPermission> permissions = entry.getValue();

            // Trova l'ItemTypeSet associato a questa configurazione
            // Nota: una ItemTypeConfiguration può essere in più ItemTypeSet, prendiamo il primo
            List<ItemTypeSet> itemTypeSets = itemTypeSetRepository.findByItemTypeConfigurations_IdAndTenant(
                    config.getId(), tenant);
            ItemTypeSet itemTypeSet = itemTypeSets.isEmpty() ? null : itemTypeSets.get(0);

            for (StatusOwnerPermission permission : permissions) {
                // Elimina le PermissionAssignment globali
                permissionAssignmentService.deleteAssignment("StatusOwnerPermission", permission.getId(), tenant);

                // Elimina le assegnazioni di progetto se l'ItemTypeSet è associato a progetti
                if (itemTypeSet != null) {
                    if (itemTypeSet.getProject() != null) {
                        projectPermissionAssignmentService.deleteProjectAssignment(
                                "StatusOwnerPermission",
                                permission.getId(),
                                itemTypeSet.getProject().getId(),
                                tenant
                        );
                    } else {
                        for (Project project : itemTypeSet.getProjectsAssociation()) {
                            projectPermissionAssignmentService.deleteProjectAssignment(
                                    "StatusOwnerPermission",
                                    permission.getId(),
                                    project.getId(),
                                    tenant
                            );
                        }
                    }
                }

                // Elimina la permission
                statusOwnerPermissionRepository.delete(permission);
            }
        }

        // Flush esplicito per assicurarsi che le delete delle StatusOwnerPermission
        // siano eseguite nel database prima di eliminare i WorkflowStatus
        // Questo evita errori di foreign key constraint
        entityManager.flush();
    }

    /**
     * Rimuove le FieldStatusPermissions orfane per gli Status rimossi
     */
    public void removeOrphanedFieldStatusPermissionsForStatuses(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedWorkflowStatusIds
    ) {
        if (removedWorkflowStatusIds == null || removedWorkflowStatusIds.isEmpty()) {
            return;
        }

        // Trova direttamente tutte le FieldStatusPermission per gli stati rimossi
        // Questo garantisce che troviamo tutte le permission, anche se non sono associate
        // a un ItemTypeSet che usa il workflow
        List<FieldStatusPermission> allPermissions = fieldStatusPermissionRepository
                .findByWorkflowStatusIdInAndTenant(removedWorkflowStatusIds, tenant);

        // Raggruppa le permission per ItemTypeConfiguration per gestire le assegnazioni di progetto
        Map<ItemTypeConfiguration, List<FieldStatusPermission>> permissionsByConfig = allPermissions.stream()
                .collect(Collectors.groupingBy(FieldStatusPermission::getItemTypeConfiguration));

        for (Map.Entry<ItemTypeConfiguration, List<FieldStatusPermission>> entry : permissionsByConfig.entrySet()) {
            ItemTypeConfiguration config = entry.getKey();
            List<FieldStatusPermission> permissions = entry.getValue();

            // Trova l'ItemTypeSet associato a questa configurazione
            // Nota: una ItemTypeConfiguration può essere in più ItemTypeSet, prendiamo il primo
            List<ItemTypeSet> itemTypeSets = itemTypeSetRepository.findByItemTypeConfigurations_IdAndTenant(
                    config.getId(), tenant);
            ItemTypeSet itemTypeSet = itemTypeSets.isEmpty() ? null : itemTypeSets.get(0);

            for (FieldStatusPermission permission : permissions) {
                // Elimina le PermissionAssignment globali
                permissionAssignmentService.deleteAssignment("FieldStatusPermission", permission.getId(), tenant);

                // Elimina le assegnazioni di progetto se l'ItemTypeSet è associato a progetti
                if (itemTypeSet != null) {
                    if (itemTypeSet.getProject() != null) {
                        projectPermissionAssignmentService.deleteProjectAssignment(
                                "FieldStatusPermission",
                                permission.getId(),
                                itemTypeSet.getProject().getId(),
                                tenant
                        );
                    } else {
                        for (Project project : itemTypeSet.getProjectsAssociation()) {
                            projectPermissionAssignmentService.deleteProjectAssignment(
                                    "FieldStatusPermission",
                                    permission.getId(),
                                    project.getId(),
                                    tenant
                            );
                        }
                    }
                }

                // Elimina la permission
                fieldStatusPermissionRepository.delete(permission);
            }
        }

        // Flush esplicito per assicurarsi che le delete delle FieldStatusPermission
        // siano eseguite nel database prima di eliminare i WorkflowStatus
        // Questo evita errori di foreign key constraint
        entityManager.flush();
    }
}


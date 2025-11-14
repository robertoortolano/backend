package com.example.demo.service.workflow;

import com.example.demo.dto.TransitionUpdateDto;
import com.example.demo.dto.WorkflowStatusUpdateDto;
import com.example.demo.dto.WorkflowUpdateDto;
import com.example.demo.dto.WorkflowViewDto;
import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.metadata.WorkflowNode;
import com.example.demo.metadata.WorkflowNodeRepository;
import com.example.demo.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WorkflowRemovalService {

    private final TransitionRepository transitionRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowRepository workflowRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final WorkflowPermissionCleanupService workflowPermissionCleanupService;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final EntityManager entityManager;
    private final DtoMapperFacade dtoMapper;
    private final WorkflowStatusUpdater workflowStatusUpdater;
    private final WorkflowTransitionSynchronizer workflowTransitionSynchronizer;
    private final WorkflowEdgeManager workflowEdgeManager;

    /**
     * Rimuove le ExecutorPermissions per una singola Transition
     */
    public void removeExecutorPermissionsForTransition(Tenant tenant, Long transitionId) {
        // Trova tutte le ExecutorPermissions per questa Transition, filtrate per Tenant (sicurezza)
        List<ExecutorPermission> permissions = executorPermissionRepository
                .findByTransitionIdAndTenant(transitionId, tenant);

        if (permissions.isEmpty()) {
            return;
        }

        Transition transition = transitionRepository.findByTransitionIdAndTenant(transitionId, tenant)
                .orElseThrow(() -> new ApiException("Transition not found: " + transitionId));

        workflowPermissionCleanupService.removeOrphanedExecutorPermissions(
                tenant,
                transition.getWorkflow().getId(),
                Set.of(transitionId)
        );
    }

    /**
     * Rimuove una Transition in modo sicuro
     */
    public void removeTransition(Tenant tenant, Long transitionId) {
        Transition transition = transitionRepository.findByIdAndTenant(transitionId, tenant)
                .orElseThrow(() -> new ApiException("Transition not found"));

        if (transition.getWorkflow().isDefaultWorkflow()) {
            throw new ApiException("Default Workflow transition cannot be deleted");
        }

        // Prima rimuovi le ExecutorPermissions associate
        removeExecutorPermissionsForTransition(tenant, transitionId);

        // Poi elimina la Transition
        transitionRepository.deleteById(transitionId);
    }

    /**
     * Rimuove le StatusOwnerPermissions per un singolo Status
     */
    public void removeStatusOwnerPermissionsForStatus(Tenant tenant, Long workflowStatusId) {
        // Trova tutte le StatusOwnerPermissions per questo WorkflowStatus, filtrate per Tenant (sicurezza)
        List<StatusOwnerPermission> permissions = statusOwnerPermissionRepository
                .findByWorkflowStatusIdAndTenant(workflowStatusId, tenant);

        // Rimuovi tutte le permission
        for (StatusOwnerPermission permission : permissions) {
            statusOwnerPermissionRepository.delete(permission);
        }
    }

    /**
     * Rimuove uno Status in modo sicuro
     */
    public void removeStatus(Tenant tenant, Long workflowStatusId) {
        WorkflowStatus workflowStatus = workflowStatusRepository.findByIdAndTenant(workflowStatusId, tenant)
                .orElseThrow(() -> new ApiException("WorkflowStatus not found"));

        if (workflowStatus.getWorkflow().isDefaultWorkflow()) {
            throw new ApiException("Default Workflow status cannot be deleted");
        }

        // Prima rimuovi le StatusOwnerPermissions associate
        removeStatusOwnerPermissionsForStatus(tenant, workflowStatusId);

        // Poi elimina il WorkflowStatus
        workflowStatusRepository.deleteById(workflowStatusId);
    }

    /**
     * Conferma la rimozione delle Transition dopo l'analisi degli impatti
     */
    public WorkflowViewDto confirmTransitionRemoval(Long workflowId, WorkflowUpdateDto dto, Tenant tenant) {
        Workflow workflow = workflowRepository.findByIdAndTenant(workflowId, tenant)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        // Identifica le Transition che verranno rimosse
        Set<Long> existingTransitionIds = workflow.getTransitions().stream()
                .map(Transition::getId)
                .collect(Collectors.toSet());

        Set<Long> newTransitionIds = dto.transitions().stream()
                .filter(t -> t.id() != null)
                .map(TransitionUpdateDto::id)
                .collect(Collectors.toSet());

        Set<Long> removedTransitionIds = existingTransitionIds.stream()
                .filter(id -> !newTransitionIds.contains(id))
                .collect(Collectors.toSet());

        // Rimuovi le ExecutorPermissions orfane per le Transition rimosse
        if (!removedTransitionIds.isEmpty()) {
            workflowPermissionCleanupService.removeOrphanedExecutorPermissions(tenant, workflowId, removedTransitionIds);
        }

        // Procedi con l'aggiornamento normale del workflow
        return performWorkflowUpdate(workflow, dto, tenant);
    }

    /**
     * Conferma la rimozione degli Status dopo l'analisi degli impatti
     */
    public WorkflowViewDto confirmStatusRemoval(Long workflowId, WorkflowUpdateDto dto, Tenant tenant) {
        Workflow workflow = workflowRepository.findByIdAndTenant(workflowId, tenant)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        // Identifica gli Status che verranno rimossi
        Set<Long> existingStatusIds = workflow.getStatuses().stream()
                .map(WorkflowStatus::getId)
                .collect(Collectors.toSet());

        Set<Long> newStatusIds = dto.workflowStatuses().stream()
                .filter(ws -> ws.id() != null)
                .map(WorkflowStatusUpdateDto::id)
                .collect(Collectors.toSet());

        Set<Long> removedStatusIds = existingStatusIds.stream()
                .filter(id -> !newStatusIds.contains(id))
                .collect(Collectors.toSet());

        // Rimuovi le StatusOwnerPermissions orfane per gli Status rimossi
        if (!removedStatusIds.isEmpty()) {
            workflowPermissionCleanupService.removeOrphanedStatusOwnerPermissions(tenant, workflowId, removedStatusIds);
            workflowPermissionCleanupService.removeOrphanedFieldStatusPermissionsForStatuses(tenant, workflowId, removedStatusIds);

            List<WorkflowStatus> statusesToRemove = removedStatusIds.stream()
                    .map(statusId -> workflowStatusRepository.findByIdAndTenantWithAssociations(statusId, tenant).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (!statusesToRemove.isEmpty()) {
                // PRIMA: Identifica le transizioni che verranno rimosse e elimina le permission associate
                // Questo deve essere fatto PRIMA di rimuovere le transizioni dalle collezioni
                // per evitare problemi di foreign key constraint durante il flush di Hibernate
                Set<Long> removedTransitionIds = statusesToRemove.stream()
                        .flatMap(status -> Stream.concat(
                                status.getOutgoingTransitions().stream(),
                                status.getIncomingTransitions().stream()))
                        .map(Transition::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                if (!removedTransitionIds.isEmpty()) {
                    // Elimina le permission PRIMA di rimuovere le transizioni dalle collezioni
                    workflowPermissionCleanupService.removeOrphanedExecutorPermissions(tenant, workflowId, removedTransitionIds);
                }

                // POI: Rimuovi gli stati dalle collezioni (questo rimuoverà anche le transizioni dalle collezioni)
                workflow.getStatuses().removeAll(statusesToRemove);

                // Flush esplicito dopo aver rimosso gli stati dalla collezione per assicurarsi che
                // tutte le delete delle permission siano state eseguite nel database prima di
                // procedere con altre operazioni che potrebbero attivare un auto-flush
                entityManager.flush();

                if (!removedTransitionIds.isEmpty()) {
                    List<Transition> transitionsToRemove = transitionRepository.findAllById(removedTransitionIds);
                    workflowPermissionCleanupService.cleanupObsoleteTransitions(tenant, transitionsToRemove);
                }

                for (WorkflowStatus workflowStatus : statusesToRemove) {
                    WorkflowNode node = workflowStatus.getNode();
                    if (node != null) {
                        workflowNodeRepository.delete(node);
                        workflowStatus.setNode(null);
                    }

                    workflowStatus.getOutgoingTransitions().clear();
                    workflowStatus.getIncomingTransitions().clear();
                    workflowStatus.getOwners().clear();

                    workflowStatusRepository.delete(workflowStatus);
                }
            }
        }

        // Procedi con l'aggiornamento normale del workflow
        return performWorkflowUpdate(workflow, dto, tenant);
    }

    private WorkflowViewDto performWorkflowUpdate(Workflow workflow, WorkflowUpdateDto dto, Tenant tenant) {
        workflow.setName(dto.name());

        WorkflowStatusUpdateResult statusUpdateResult = workflowStatusUpdater.updateWorkflowStatuses(workflow, dto, tenant);
        workflowPermissionCleanupService.handleNewWorkflowStatuses(tenant, workflow, statusUpdateResult.getNewWorkflowStatusIds());

        WorkflowTransitionSyncResult transitionSyncResult = workflowTransitionSynchronizer.synchronizeTransitions(
                workflow,
                dto,
                tenant,
                statusUpdateResult.getStatusByStatusId()
        );

        workflowPermissionCleanupService.cleanupObsoleteTransitions(tenant, transitionSyncResult.getObsoleteTransitions());
        workflowPermissionCleanupService.handleNewTransitions(tenant, workflow, transitionSyncResult.getExistingTransitionIdsBeforeUpdate());

        workflowEdgeManager.synchronizeEdges(workflow, dto, tenant, transitionSyncResult);

        workflow = workflowRepository.save(workflow);
        return dtoMapper.toWorkflowViewDto(workflow);
    }
}


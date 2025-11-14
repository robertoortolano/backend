package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.*;
import com.example.demo.metadata.WorkflowNodeRepository;
import com.example.demo.service.workflow.WorkflowEdgeManager;
import com.example.demo.service.workflow.WorkflowPermissionCleanupService;
import com.example.demo.service.workflow.WorkflowRemovalService;
import com.example.demo.service.workflow.WorkflowStatusUpdateResult;
import com.example.demo.service.workflow.WorkflowStatusUpdater;
import com.example.demo.service.workflow.WorkflowTransitionSyncResult;
import com.example.demo.service.workflow.WorkflowTransitionSynchronizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final TransitionRepository transitionRepository;
    private final WorkflowCreationService workflowCreationService;
    private final ItemTypeSetRepository itemTypeSetRepository;
    private final WorkflowLookup workflowLookup;
    private final DtoMapperFacade dtoMapper;
    private final ItemTypeSetLookup itemTypeSetLookup;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final WorkflowStatusUpdater workflowStatusUpdater;
    private final WorkflowTransitionSynchronizer workflowTransitionSynchronizer;
    private final WorkflowEdgeManager workflowEdgeManager;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final WorkflowPermissionCleanupService workflowPermissionCleanupService;
    private final WorkflowRemovalService workflowRemovalService;
    
    // Servizio per analisi impatti workflow
    private final WorkflowImpactAnalysisService workflowImpactAnalysisService;

    @Transactional
    public WorkflowViewDto createGlobal(WorkflowCreateDto dto, Tenant tenant) {
        return workflowCreationService.createGlobal(dto, tenant);
    }

    /**
     * Crea un workflow per progetto
     * IMPORTANTE: I workflow di progetto usano gli Status globali (tenant-level)
     */
    @Transactional
    public WorkflowViewDto createForProject(WorkflowCreateDto dto, Tenant tenant, Long projectId) {
        return workflowCreationService.createForProject(dto, tenant, projectId);
    }

    @Transactional
    public WorkflowViewDto updateWorkflow(Long workflowId, WorkflowUpdateDto dto, Tenant tenant) {
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

        // Se ci sono Transition rimosse, analizza gli impatti
        if (!removedTransitionIds.isEmpty()) {
            TransitionRemovalImpactDto impact = analyzeTransitionRemovalImpact(tenant, workflowId, removedTransitionIds);
            
            // Se ci sono ExecutorPermissions con assegnazioni, restituisci il report per conferma
            boolean executorAssignments = impact.getExecutorPermissions() != null
                    && impact.getExecutorPermissions().stream().anyMatch(TransitionRemovalImpactDto.PermissionImpact::isHasAssignments);
            boolean fieldStatusAssignments = impact.getFieldStatusPermissions() != null
                    && impact.getFieldStatusPermissions().stream().anyMatch(TransitionRemovalImpactDto.FieldStatusPermissionImpact::isHasAssignments);
            boolean statusOwnerAssignments = impact.getStatusOwnerPermissions() != null
                    && impact.getStatusOwnerPermissions().stream().anyMatch(TransitionRemovalImpactDto.StatusOwnerPermissionImpact::isHasAssignments);
            if (executorAssignments || fieldStatusAssignments || statusOwnerAssignments) {
                throw new ApiException("TRANSITION_REMOVAL_IMPACT: rilevate permission con assegnazioni per le transition rimosse");
            }
        }

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

        // Se ci sono Status rimossi, analizza gli impatti
        if (!removedStatusIds.isEmpty()) {
            StatusRemovalImpactDto impact = analyzeStatusRemovalImpact(tenant, workflowId, removedStatusIds);
            
            // Se ci sono StatusOwnerPermissions con assegnazioni, restituisci il report per conferma
            boolean statusOwnerAssignments = impact.getStatusOwnerPermissions() != null
                    && impact.getStatusOwnerPermissions().stream().anyMatch(StatusRemovalImpactDto.PermissionImpact::isHasAssignments);
            boolean statusExecutorAssignments = impact.getExecutorPermissions() != null
                    && impact.getExecutorPermissions().stream().anyMatch(StatusRemovalImpactDto.ExecutorPermissionImpact::isHasAssignments);
            boolean statusFieldAssignments = impact.getFieldStatusPermissions() != null
                    && impact.getFieldStatusPermissions().stream().anyMatch(StatusRemovalImpactDto.FieldStatusPermissionImpact::isHasAssignments);
            if (statusOwnerAssignments || statusExecutorAssignments || statusFieldAssignments) {
                throw new ApiException("STATUS_REMOVAL_IMPACT: rilevate permission con assegnazioni per gli elementi rimossi dal workflow");
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

    public void delete(Tenant tenant, Long workflowId) {
        Workflow workflow = workflowLookup.getByIdEntity(tenant, workflowId);
        if (!workflowLookup.isNotInAnyItemTypeSet(workflowId, tenant)) {
            throw new ApiException("Workflow is used in an ItemType and cannot be deleted");
        }
        workflowRepository.delete(workflow);
    }

    @Transactional(readOnly = true)
    public WorkflowDetailDto getWorkflowDetail(Long workflowId, Tenant tenant) {
        Workflow workflow = workflowLookup.getByIdEntity(tenant, workflowId);
        List<ItemTypeSet> itemTypeSets = itemTypeSetRepository.findByItemTypeConfigurationsWorkflowIdAndTenant(workflowId, tenant);
        
        return dtoMapper.toWorkflowDetailDto(workflow, itemTypeSets);
    }

    // ========================
    // TRANSITION REMOVAL IMPACT MANAGEMENT
    // ========================
    
    /**
     * Analizza gli impatti della rimozione di Transition da un Workflow
     */
    @Transactional(readOnly = true)
    public TransitionRemovalImpactDto analyzeTransitionRemovalImpact(
            Tenant tenant, 
            Long workflowId, 
            Set<Long> removedTransitionIds
    ) {
        // Delega l'analisi degli impatti al servizio dedicato
        return workflowImpactAnalysisService.analyzeTransitionRemovalImpact(tenant, workflowId, removedTransitionIds);
    }
    
    /**
     * Rimuove le ExecutorPermissions orfane per le Transition rimosse
     */
    public void removeOrphanedExecutorPermissions(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedTransitionIds
    ) {
        workflowPermissionCleanupService.removeOrphanedExecutorPermissions(tenant, workflowId, removedTransitionIds);
    }

    /**
     * Rimuove le ExecutorPermissions per una singola Transition
     */
    public void removeExecutorPermissionsForTransition(Tenant tenant, Long transitionId) {
        workflowRemovalService.removeExecutorPermissionsForTransition(tenant, transitionId);
    }

    /**
     * Rimuove una Transition in modo sicuro
     */
    public void removeTransition(Tenant tenant, Long transitionId) {
        workflowRemovalService.removeTransition(tenant, transitionId);
    }

    /**
     * Conferma la rimozione delle Transition dopo l'analisi degli impatti
     */
    @Transactional
    public WorkflowViewDto confirmTransitionRemoval(Long workflowId, WorkflowUpdateDto dto, Tenant tenant) {
        return workflowRemovalService.confirmTransitionRemoval(workflowId, dto, tenant);
    }
    
    /**
     * Analizza gli impatti della rimozione di Status da un Workflow
     * Include anche le transizioni che verranno rimosse (entranti e uscenti dagli stati rimossi)
     */
    @Transactional(readOnly = true)
    public StatusRemovalImpactDto analyzeStatusRemovalImpact(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedStatusIds
    ) {
        // Delega l'analisi degli impatti al servizio dedicato
        return workflowImpactAnalysisService.analyzeStatusRemovalImpact(tenant, workflowId, removedStatusIds);
    }

    public StatusRemovalImpactDto analyzeStatusRemovalImpact(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedStatusIds,
            Set<Long> actuallyRemovedTransitionIds
    ) {
        // Delega l'analisi degli impatti al servizio dedicato con le transizioni effettivamente rimosse
        return workflowImpactAnalysisService.analyzeStatusRemovalImpact(
                tenant, workflowId, removedStatusIds, actuallyRemovedTransitionIds);
    }
    
    /**
     * Rimuove le StatusOwnerPermissions orfane per gli Status rimossi
     */
    public void removeOrphanedStatusOwnerPermissions(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedStatusIds
    ) {
        workflowPermissionCleanupService.removeOrphanedStatusOwnerPermissions(tenant, workflowId, removedStatusIds);
    }

    /**
     * Rimuove le StatusOwnerPermissions per un singolo Status
     */
    public void removeStatusOwnerPermissionsForStatus(Tenant tenant, Long workflowStatusId) {
        workflowRemovalService.removeStatusOwnerPermissionsForStatus(tenant, workflowStatusId);
    }

    /**
     * Rimuove uno Status in modo sicuro
     */
    public void removeStatus(Tenant tenant, Long workflowStatusId) {
        workflowRemovalService.removeStatus(tenant, workflowStatusId);
    }

    /**
     * Conferma la rimozione degli Status dopo l'analisi degli impatti
     */
    @Transactional
    public WorkflowViewDto confirmStatusRemoval(Long workflowId, WorkflowUpdateDto dto, Tenant tenant) {
        return workflowRemovalService.confirmStatusRemoval(workflowId, dto, tenant);
    }
    
    /**
     * Ottiene gli ID degli Status di un Workflow
     */
    public Set<Long> getWorkflowStatusIds(Long workflowId, Tenant tenant) {
        Workflow workflow = workflowRepository.findByIdAndTenant(workflowId, tenant)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        return workflow.getStatuses().stream()
                .map(WorkflowStatus::getId)
                .collect(Collectors.toSet());
    }

    public Set<Long> getWorkflowTransitionIds(Long workflowId, Tenant tenant) {
        Workflow workflow = workflowRepository.findByIdAndTenant(workflowId, tenant)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        return workflow.getTransitions().stream()
                .map(Transition::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}


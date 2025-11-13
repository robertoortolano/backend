package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.*;
import com.example.demo.metadata.WorkflowNode;
import com.example.demo.metadata.WorkflowNodeRepository;
import com.example.demo.service.workflow.WorkflowEdgeManager;
import com.example.demo.service.workflow.WorkflowPermissionCleanupService;
import com.example.demo.service.workflow.WorkflowStatusUpdateResult;
import com.example.demo.service.workflow.WorkflowStatusUpdater;
import com.example.demo.service.workflow.WorkflowTransitionSyncResult;
import com.example.demo.service.workflow.WorkflowTransitionSynchronizer;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    
    // Servizi per PermissionAssignment (nuova struttura)
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;
    
    // Servizio per analisi impatti workflow
    private final WorkflowImpactAnalysisService workflowImpactAnalysisService;
    
    // EntityManager per gestire il flush esplicito
    private final EntityManager entityManager;

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

    // --- Helpers comuni ---


    /**
     * Trova tutti gli ItemTypeSet che usano un Workflow specifico
     */
    private List<ItemTypeSet> findItemTypeSetsUsingWorkflow(Long workflowId, Tenant tenant) {
        return itemTypeSetLookup.findByWorkflowId(workflowId, tenant);
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

        List<ItemTypeSet> affectedItemTypeSets = findItemTypeSetsUsingWorkflow(transition.getWorkflow().getId(), tenant);
        removeOrphanedExecutorPermissions(affectedItemTypeSets, Set.of(transitionId), tenant);
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
     * Conferma la rimozione delle Transition dopo l'analisi degli impatti
     */
    @Transactional
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
            removeOrphanedExecutorPermissions(tenant, workflowId, removedTransitionIds);
        }

        // Procedi con l'aggiornamento normale del workflow
        return performWorkflowUpdate(workflow, dto, tenant);
    }
    
    /**
     * Ottiene i nomi delle Transition rimosse nel formato "Nome (Da Stato -> A Stato)" o "Da Stato -> A Stato"
     */
    private List<String> getTransitionNames(Set<Long> transitionIds, Tenant tenant) {
        return transitionIds.stream()
                .map(id -> {
                    try {
                        Transition transition = transitionRepository.findByTransitionIdAndTenant(id, tenant).orElse(null);
                        if (transition == null) {
                            return "Transition " + id;
                        }
                        return formatTransitionName(transition);
                    } catch (Exception e) {
                        return "Transition " + id;
                    }
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Formatta il nome di una Transition nel formato "Nome (Da Stato -> A Stato)" o "Da Stato -> A Stato"
     */
    private String formatTransitionName(Transition transition) {
        String fromStatusName = transition.getFromStatus().getStatus().getName();
        String toStatusName = transition.getToStatus().getStatus().getName();
        String transitionName = transition.getName();
        
        // Se la transizione ha un nome, usa il formato "Nome (Da Stato -> A Stato)"
        if (transitionName != null && !transitionName.trim().isEmpty()) {
            return String.format("%s (%s -> %s)", transitionName.trim(), fromStatusName, toStatusName);
        } else {
            // Se non ha nome, usa solo "Da Stato -> A Stato"
            return String.format("%s -> %s", fromStatusName, toStatusName);
        }
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

    private void removeOrphanedFieldStatusPermissionsForStatuses(
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
    
    /**
     * Conferma la rimozione degli Status dopo l'analisi degli impatti
     */
    @Transactional
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
            removeOrphanedStatusOwnerPermissions(tenant, workflowId, removedStatusIds);
            removeOrphanedFieldStatusPermissionsForStatuses(tenant, workflowId, removedStatusIds);

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
                    removeOrphanedExecutorPermissions(tenant, workflowId, removedTransitionIds);
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
}


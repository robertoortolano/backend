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
        Workflow workflow = workflowLookup.getByIdEntity(tenant, workflowId);
        
        // Trova tutti gli ItemTypeSet che usano questo Workflow
        List<ItemTypeSet> allItemTypeSetsUsingWorkflow = findItemTypeSetsUsingWorkflow(workflowId, tenant);
        
        // Analizza le ExecutorPermissions che verranno rimosse
        List<TransitionRemovalImpactDto.PermissionImpact> executorPermissions = 
                analyzeExecutorPermissionImpacts(allItemTypeSetsUsingWorkflow, removedTransitionIds);

        // Identifica gli WorkflowStatus coinvolti dalle transition rimosse
        Set<Long> relatedWorkflowStatusIds = new HashSet<>();
        for (Long transitionId : removedTransitionIds) {
            transitionRepository.findByTransitionIdAndTenant(transitionId, tenant).ifPresent(transition -> {
                if (transition.getFromStatus() != null && transition.getFromStatus().getId() != null) {
                    relatedWorkflowStatusIds.add(transition.getFromStatus().getId());
                }
                if (transition.getToStatus() != null && transition.getToStatus().getId() != null) {
                    relatedWorkflowStatusIds.add(transition.getToStatus().getId());
                }
            });
        }

        List<TransitionRemovalImpactDto.FieldStatusPermissionImpact> fieldStatusPermissions =
                relatedWorkflowStatusIds.isEmpty()
                        ? new ArrayList<>()
                        : analyzeFieldStatusPermissionImpactsForTransitions(
                                allItemTypeSetsUsingWorkflow,
                                relatedWorkflowStatusIds,
                                tenant
                        );
        
        List<TransitionRemovalImpactDto.StatusOwnerPermissionImpact> statusOwnerPermissions =
                relatedWorkflowStatusIds.isEmpty()
                        ? new ArrayList<>()
                        : analyzeStatusOwnerPermissionImpactsForTransitions(
                                allItemTypeSetsUsingWorkflow,
                                relatedWorkflowStatusIds,
                                tenant
                        );
        
        // Calcola solo gli ItemTypeSet che hanno effettivamente impatti (permissions con ruoli assegnati)
        Set<Long> itemTypeSetIdsWithImpact = new HashSet<>();
        executorPermissions.forEach(perm -> itemTypeSetIdsWithImpact.add(perm.getItemTypeSetId()));
        statusOwnerPermissions.forEach(perm -> itemTypeSetIdsWithImpact.add(perm.getItemTypeSetId()));
        fieldStatusPermissions.forEach(perm -> itemTypeSetIdsWithImpact.add(perm.getItemTypeSetId()));
        
        List<ItemTypeSet> affectedItemTypeSets = allItemTypeSetsUsingWorkflow.stream()
                .filter(its -> itemTypeSetIdsWithImpact.contains(its.getId()))
                .collect(Collectors.toList());
        
        // Calcola le statistiche usando assignedRoles dal DTO (già popolato da PermissionAssignment)
        int totalExecutorPermissions = executorPermissions.size();
        int totalStatusOwnerPermissions = statusOwnerPermissions.size();
        int totalFieldStatusPermissions = fieldStatusPermissions.size();
        int totalRoleAssignments = executorPermissions.stream()
                .mapToInt(perm -> perm.getAssignedRoles() != null ? perm.getAssignedRoles().size() : 0)
                .sum()
                + statusOwnerPermissions.stream()
                .mapToInt(perm -> perm.getAssignedRoles() != null ? perm.getAssignedRoles().size() : 0)
                .sum()
                + fieldStatusPermissions.stream()
                .mapToInt(perm -> perm.getAssignedRoles() != null ? perm.getAssignedRoles().size() : 0)
                .sum();
        // Calcola totalGrantAssignments: conta i grant globali (solo se hanno assegnazioni)
        int totalGrantAssignments = (int) executorPermissions.stream()
                .filter(perm -> perm.getGrantId() != null)
                .count()
                + (int) statusOwnerPermissions.stream()
                .filter(perm -> perm.getGrantId() != null)
                .count()
                + (int) fieldStatusPermissions.stream()
                .filter(perm -> perm.getGrantId() != null)
                .count();
        
        // Ottieni i nomi delle Transition rimosse
        List<String> removedTransitionNames = getTransitionNames(removedTransitionIds, tenant);
        
        return TransitionRemovalImpactDto.builder()
                .workflowId(workflowId)
                .workflowName(workflow.getName())
                .removedTransitionIds(new ArrayList<>(removedTransitionIds))
                .removedTransitionNames(removedTransitionNames)
                .affectedItemTypeSets(mapItemTypeSetImpacts(
                        affectedItemTypeSets,
                        executorPermissions,
                        statusOwnerPermissions,
                        fieldStatusPermissions))
                .executorPermissions(executorPermissions)
                .statusOwnerPermissions(statusOwnerPermissions)
                .fieldStatusPermissions(fieldStatusPermissions)
                .totalAffectedItemTypeSets(affectedItemTypeSets.size()) // Solo quelli con impatti effettivi
                .totalExecutorPermissions(totalExecutorPermissions)
                .totalStatusOwnerPermissions(totalStatusOwnerPermissions)
                .totalFieldStatusPermissions(totalFieldStatusPermissions)
                .totalGrantAssignments(totalGrantAssignments)
                .totalRoleAssignments(totalRoleAssignments)
                .build();
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
     * Analizza le ExecutorPermissions che verranno rimosse
     */
    private List<TransitionRemovalImpactDto.PermissionImpact> analyzeExecutorPermissionImpacts(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedTransitionIds
    ) {
        List<TransitionRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                // Trova le ExecutorPermissions per le Transition rimosse
                List<ExecutorPermission> permissions = executorPermissionRepository
                        .findByItemTypeConfigurationAndTransitionIdIn(config, removedTransitionIds);
                
                for (ExecutorPermission permission : permissions) {
                    Transition transition = permission.getTransition();
                    
                    // Recupera ruoli globali da PermissionAssignment
                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "ExecutorPermission", permission.getId(), itemTypeSet.getTenant());
                    
                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());
                    
                    // Recupera Grant globale se presente
                    Long globalGrantId = null;
                    String globalGrantName = null;
                    if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                        Grant grant = assignmentOpt.get().getGrant();
                        globalGrantId = grant.getId();
                        globalGrantName = grant.getRole() != null 
                                ? grant.getRole().getName() 
                                : "Grant globale";
                    }
                    
                    // Grant e ruoli di progetto - recupera da ProjectPermissionAssignmentService
                    List<TransitionRemovalImpactDto.ProjectGrantInfo> projectGrantsList = new ArrayList<>();
                    List<TransitionRemovalImpactDto.ProjectRoleInfo> projectRolesList = new ArrayList<>();
                    // Se è un ItemTypeSet di progetto, controlla solo quel progetto
                    if (itemTypeSet.getProject() != null) {
                        Optional<PermissionAssignment> projectAssignmentOpt = 
                                projectPermissionAssignmentService.getProjectAssignment(
                                        "ExecutorPermission", permission.getId(), 
                                        itemTypeSet.getProject().getId(), itemTypeSet.getTenant());
                        if (projectAssignmentOpt.isPresent()) {
                            PermissionAssignment projectAssignment = projectAssignmentOpt.get();
                            // Aggiungi grant di progetto se presente
                            if (projectAssignment.getGrant() != null) {
                                projectGrantsList.add(TransitionRemovalImpactDto.ProjectGrantInfo.builder()
                                        .projectId(itemTypeSet.getProject().getId())
                                        .projectName(itemTypeSet.getProject().getName())
                                        .build());
                            }
                            // IMPORTANTE: Separare i ruoli di progetto dai ruoli globali
                            Set<Role> projectRoles = projectAssignment.getRoles();
                            if (projectRoles != null && !projectRoles.isEmpty()) {
                                List<String> projectRoleNames = projectRoles.stream()
                                        .map(Role::getName)
                                        .collect(Collectors.toList());
                                projectRolesList.add(TransitionRemovalImpactDto.ProjectRoleInfo.builder()
                                        .projectId(itemTypeSet.getProject().getId())
                                        .projectName(itemTypeSet.getProject().getName())
                                        .roles(projectRoleNames)
                                        .build());
                            }
                        }
                    } else {
                        // Se è un ItemTypeSet globale, controlla tutti i progetti associati
                        for (Project project : itemTypeSet.getProjectsAssociation()) {
                            Optional<PermissionAssignment> projectAssignmentOpt = 
                                    projectPermissionAssignmentService.getProjectAssignment(
                                            "ExecutorPermission", permission.getId(), 
                                            project.getId(), itemTypeSet.getTenant());
                            if (projectAssignmentOpt.isPresent()) {
                                PermissionAssignment projectAssignment = projectAssignmentOpt.get();
                                // Aggiungi grant di progetto se presente
                                if (projectAssignment.getGrant() != null) {
                                    projectGrantsList.add(TransitionRemovalImpactDto.ProjectGrantInfo.builder()
                                            .projectId(project.getId())
                                            .projectName(project.getName())
                                            .build());
                                }
                                // IMPORTANTE: Separare i ruoli di progetto dai ruoli globali
                                Set<Role> projectRoles = projectAssignment.getRoles();
                                if (projectRoles != null && !projectRoles.isEmpty()) {
                                    List<String> projectRoleNames = projectRoles.stream()
                                            .map(Role::getName)
                                            .collect(Collectors.toList());
                                    projectRolesList.add(TransitionRemovalImpactDto.ProjectRoleInfo.builder()
                                            .projectId(project.getId())
                                            .projectName(project.getName())
                                            .roles(projectRoleNames)
                                            .build());
                                }
                            }
                        }
                    }
                    
                    // Calcola hasAssignments: true se ha ruoli O grant (globali o di progetto)
                    boolean hasGlobalRoles = !assignedRoles.isEmpty();
                    boolean hasProjectRoles = !projectRolesList.isEmpty();
                    boolean hasGlobalGrant = globalGrantId != null;
                    boolean hasProjectGrant = !projectGrantsList.isEmpty();
                    boolean hasAssignments = hasGlobalRoles || hasProjectRoles || hasGlobalGrant || hasProjectGrant;
                    
                    // Solo se ha assegnazioni (ruoli o grant)
                    if (hasAssignments) {
                        // Per Transition, canBePreserved è difficile da calcolare (dovrebbe verificare se esiste un'altra Transition equivalente)
                        // Per ora impostiamo a false
                        boolean canBePreserved = false;
                        boolean defaultPreserve = false;
                        
                        // Salva solo il nome della transition (senza formattazione)
                        // La formattazione verrà fatta nel frontend
                        String transitionNameOnly = transition.getName() != null && !transition.getName().trim().isEmpty()
                                ? transition.getName().trim()
                                : null;
                        
                        impacts.add(TransitionRemovalImpactDto.PermissionImpact.builder()
                                .permissionId(permission.getId())
                                .permissionType("EXECUTORS")
                                .itemTypeSetId(itemTypeSet.getId())
                                .itemTypeSetName(itemTypeSet.getName())
                                .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                                .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                                .transitionId(transition.getId())
                                .transitionName(transitionNameOnly)
                                .fromStatusName(transition.getFromStatus().getStatus().getName())
                                .toStatusName(transition.getToStatus().getStatus().getName())
                                // RIMOSSO: roleId - ItemTypeSetRole eliminata
                                .grantId(globalGrantId)
                                .grantName(globalGrantName)
                                .assignedRoles(assignedRoles) // Solo ruoli globali
                                .projectAssignedRoles(projectRolesList) // Ruoli di progetto separati
                                .hasAssignments(true)
                                .canBePreserved(canBePreserved)
                                .defaultPreserve(defaultPreserve)
                                .projectGrants(projectGrantsList)
                                .build());
                    }
                }
            }
        }
        
        return impacts;
    }

    private List<TransitionRemovalImpactDto.FieldStatusPermissionImpact> analyzeFieldStatusPermissionImpactsForTransitions(
            List<ItemTypeSet> itemTypeSets,
            Set<Long> workflowStatusIds,
            Tenant tenant
    ) {
        List<TransitionRemovalImpactDto.FieldStatusPermissionImpact> impacts = new ArrayList<>();
        if (workflowStatusIds == null || workflowStatusIds.isEmpty()) {
            return impacts;
        }

        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                List<FieldStatusPermission> permissions = fieldStatusPermissionRepository
                        .findAllByItemTypeConfigurationAndWorkflowStatusIdIn(config, workflowStatusIds);

                for (FieldStatusPermission permission : permissions) {
                    WorkflowStatus workflowStatus = permission.getWorkflowStatus();
                    if (workflowStatus == null) {
                        continue;
                    }

                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "FieldStatusPermission", permission.getId(), itemTypeSet.getTenant());

                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());

                    Long globalGrantId = null;
                    String globalGrantName = null;
                    if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                        Grant grant = assignmentOpt.get().getGrant();
                        globalGrantId = grant.getId();
                        globalGrantName = grant.getRole() != null
                                ? grant.getRole().getName()
                                : "Grant globale";
                    }

                    List<TransitionRemovalImpactDto.ProjectGrantInfo> projectGrantsList = new ArrayList<>();
                    List<TransitionRemovalImpactDto.ProjectRoleInfo> projectRolesList = new ArrayList<>();
                    if (itemTypeSet.getProject() != null) {
                        Optional<PermissionAssignment> projectAssignmentOpt =
                                projectPermissionAssignmentService.getProjectAssignment(
                                        "FieldStatusPermission",
                                        permission.getId(),
                                        itemTypeSet.getProject().getId(),
                                        itemTypeSet.getTenant());
                        projectAssignmentOpt.ifPresent(projectAssignment -> {
                            if (projectAssignment.getGrant() != null) {
                                projectGrantsList.add(TransitionRemovalImpactDto.ProjectGrantInfo.builder()
                                        .projectId(itemTypeSet.getProject().getId())
                                        .projectName(itemTypeSet.getProject().getName())
                                        .build());
                            }

                            Set<Role> projectRoles = projectAssignment.getRoles();
                            if (projectRoles != null && !projectRoles.isEmpty()) {
                                List<String> projectRoleNames = projectRoles.stream()
                                        .map(Role::getName)
                                        .collect(Collectors.toList());
                                projectRolesList.add(TransitionRemovalImpactDto.ProjectRoleInfo.builder()
                                        .projectId(itemTypeSet.getProject().getId())
                                        .projectName(itemTypeSet.getProject().getName())
                                        .roles(projectRoleNames)
                                        .build());
                            }
                        });
                    } else {
                        for (Project project : itemTypeSet.getProjectsAssociation()) {
                            Optional<PermissionAssignment> projectAssignmentOpt =
                                    projectPermissionAssignmentService.getProjectAssignment(
                                            "FieldStatusPermission",
                                            permission.getId(),
                                            project.getId(),
                                            itemTypeSet.getTenant());
                            projectAssignmentOpt.ifPresent(projectAssignment -> {
                                if (projectAssignment.getGrant() != null) {
                                    projectGrantsList.add(TransitionRemovalImpactDto.ProjectGrantInfo.builder()
                                            .projectId(project.getId())
                                            .projectName(project.getName())
                                            .build());
                                }

                                Set<Role> projectRoles = projectAssignment.getRoles();
                                if (projectRoles != null && !projectRoles.isEmpty()) {
                                    List<String> projectRoleNames = projectRoles.stream()
                                            .map(Role::getName)
                                            .collect(Collectors.toList());
                                    projectRolesList.add(TransitionRemovalImpactDto.ProjectRoleInfo.builder()
                                            .projectId(project.getId())
                                            .projectName(project.getName())
                                            .roles(projectRoleNames)
                                            .build());
                                }
                            });
                        }
                    }

                    Field field = permission.getField();
                    String fieldName = field != null ? field.getName() : null;
                    Long fieldId = field != null ? field.getId() : null;

                    boolean hasGlobalRoles = !assignedRoles.isEmpty();
                    boolean hasProjectRoles = !projectRolesList.isEmpty();
                    boolean hasGlobalGrant = globalGrantId != null;
                    boolean hasProjectGrant = !projectGrantsList.isEmpty();
                    boolean hasAssignments = hasGlobalRoles || hasProjectRoles || hasGlobalGrant || hasProjectGrant;

                    if (hasAssignments) {
                        String permissionType = "FIELD_VIEWERS";
                        if (permission.getPermissionType() == FieldStatusPermission.PermissionType.EDITORS) {
                            permissionType = "FIELD_EDITORS";
                        }

                        impacts.add(TransitionRemovalImpactDto.FieldStatusPermissionImpact.builder()
                                .permissionId(permission.getId())
                                .permissionType(permissionType)
                                .itemTypeSetId(itemTypeSet.getId())
                                .itemTypeSetName(itemTypeSet.getName())
                                .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                                .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                                .fieldId(fieldId)
                                .fieldName(fieldName)
                                .workflowStatusId(workflowStatus.getId())
                                .workflowStatusName(workflowStatus.getStatus() != null ? workflowStatus.getStatus().getName() : null)
                                .statusId(workflowStatus.getStatus() != null ? workflowStatus.getStatus().getId() : null)
                                .statusName(workflowStatus.getStatus() != null ? workflowStatus.getStatus().getName() : null)
                                .grantId(globalGrantId)
                                .grantName(globalGrantName)
                                .assignedRoles(assignedRoles)
                                .projectAssignedRoles(projectRolesList)
                                .hasAssignments(true)
                                .projectGrants(projectGrantsList)
                                .build());
                    }
                }
            }
        }

        return impacts;
    }
    
    private List<TransitionRemovalImpactDto.StatusOwnerPermissionImpact> analyzeStatusOwnerPermissionImpactsForTransitions(
            List<ItemTypeSet> itemTypeSets,
            Set<Long> workflowStatusIds,
            Tenant tenant
    ) {
        List<TransitionRemovalImpactDto.StatusOwnerPermissionImpact> impacts = new ArrayList<>();
        if (workflowStatusIds == null || workflowStatusIds.isEmpty()) {
            return impacts;
        }

        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                List<StatusOwnerPermission> permissions = statusOwnerPermissionRepository
                        .findByItemTypeConfigurationAndWorkflowStatusIdIn(config, workflowStatusIds);

                for (StatusOwnerPermission permission : permissions) {
                    WorkflowStatus workflowStatus = permission.getWorkflowStatus();
                    if (workflowStatus == null) {
                        continue;
                    }

                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "StatusOwnerPermission", permission.getId(), itemTypeSet.getTenant());

                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());

                    Long globalGrantId = null;
                    String globalGrantName = null;
                    if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                        Grant grant = assignmentOpt.get().getGrant();
                        globalGrantId = grant.getId();
                        globalGrantName = grant.getRole() != null
                                ? grant.getRole().getName()
                                : "Grant globale";
                    }

                    List<TransitionRemovalImpactDto.ProjectGrantInfo> projectGrantsList = new ArrayList<>();
                    List<TransitionRemovalImpactDto.ProjectRoleInfo> projectRolesList = new ArrayList<>();

                    if (itemTypeSet.getProject() != null) {
                        Optional<PermissionAssignment> projectAssignmentOpt =
                                projectPermissionAssignmentService.getProjectAssignment(
                                        "StatusOwnerPermission",
                                        permission.getId(),
                                        itemTypeSet.getProject().getId(),
                                        itemTypeSet.getTenant());
                        if (projectAssignmentOpt.isPresent()) {
                            PermissionAssignment projectAssignment = projectAssignmentOpt.get();
                            if (projectAssignment.getGrant() != null) {
                                projectGrantsList.add(TransitionRemovalImpactDto.ProjectGrantInfo.builder()
                                        .projectId(itemTypeSet.getProject().getId())
                                        .projectName(itemTypeSet.getProject().getName())
                                        .build());
                            }
                            Set<Role> projectRoles = projectAssignment.getRoles();
                            if (projectRoles != null && !projectRoles.isEmpty()) {
                                List<String> projectRoleNames = projectRoles.stream()
                                        .map(Role::getName)
                                        .collect(Collectors.toList());
                                projectRolesList.add(TransitionRemovalImpactDto.ProjectRoleInfo.builder()
                                        .projectId(itemTypeSet.getProject().getId())
                                        .projectName(itemTypeSet.getProject().getName())
                                        .roles(projectRoleNames)
                                        .build());
                            }
                        }
                    } else {
                        for (Project project : itemTypeSet.getProjectsAssociation()) {
                            Optional<PermissionAssignment> projectAssignmentOpt =
                                    projectPermissionAssignmentService.getProjectAssignment(
                                            "StatusOwnerPermission",
                                            permission.getId(),
                                            project.getId(),
                                            itemTypeSet.getTenant());
                            if (projectAssignmentOpt.isPresent()) {
                                PermissionAssignment projectAssignment = projectAssignmentOpt.get();
                                if (projectAssignment.getGrant() != null) {
                                    projectGrantsList.add(TransitionRemovalImpactDto.ProjectGrantInfo.builder()
                                            .projectId(project.getId())
                                            .projectName(project.getName())
                                            .build());
                                }
                                Set<Role> projectRoles = projectAssignment.getRoles();
                                if (projectRoles != null && !projectRoles.isEmpty()) {
                                    List<String> projectRoleNames = projectRoles.stream()
                                            .map(Role::getName)
                                            .collect(Collectors.toList());
                                    projectRolesList.add(TransitionRemovalImpactDto.ProjectRoleInfo.builder()
                                            .projectId(project.getId())
                                            .projectName(project.getName())
                                            .roles(projectRoleNames)
                                            .build());
                                }
                            }
                        }
                    }

                    boolean hasGlobalRoles = !assignedRoles.isEmpty();
                    boolean hasProjectRoles = !projectRolesList.isEmpty();
                    boolean hasGlobalGrant = globalGrantId != null;
                    boolean hasProjectGrant = !projectGrantsList.isEmpty();
                    boolean hasAssignments = hasGlobalRoles || hasProjectRoles || hasGlobalGrant || hasProjectGrant;

                    if (!hasAssignments) {
                        continue;
                    }

                    Long statusId = workflowStatus.getStatus() != null ? workflowStatus.getStatus().getId() : null;
                    String statusName = workflowStatus.getStatus() != null ? workflowStatus.getStatus().getName() : null;
                    String workflowStatusName = workflowStatus.getStatus() != null ? workflowStatus.getStatus().getName() : null;

                    impacts.add(TransitionRemovalImpactDto.StatusOwnerPermissionImpact.builder()
                            .permissionId(permission.getId())
                            .permissionType("STATUS_OWNERS")
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .workflowStatusId(workflowStatus.getId())
                            .workflowStatusName(workflowStatusName)
                            .statusId(statusId)
                            .statusName(statusName)
                            .grantId(globalGrantId)
                            .grantName(globalGrantName)
                            .assignedRoles(assignedRoles)
                            .projectAssignedRoles(projectRolesList)
                            .projectGrants(projectGrantsList)
                            .hasAssignments(true)
                            .build());
                }
            }
        }

        return impacts;
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
        Workflow workflow = workflowRepository.findByIdAndTenant(workflowId, tenant)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        // Trova tutti gli ItemTypeSet che usano questo Workflow
        List<ItemTypeSet> allItemTypeSetsUsingWorkflow = findItemTypeSetsUsingWorkflow(workflowId, tenant);
        
        // Trova tutti gli WorkflowStatus che verranno rimossi
        List<WorkflowStatus> removedWorkflowStatuses = workflow.getStatuses().stream()
                .filter(ws -> removedStatusIds.contains(ws.getId()))
                .collect(Collectors.toList());
        
        // Trova tutte le transizioni che verranno rimosse (entranti e uscenti dagli stati rimossi)
        Set<Long> removedTransitionIds = new HashSet<>();
        for (WorkflowStatus workflowStatus : removedWorkflowStatuses) {
            // Transizioni uscenti (fromStatus)
            List<Transition> outgoingTransitions = transitionRepository.findByFromStatus(workflowStatus);
            removedTransitionIds.addAll(outgoingTransitions.stream()
                    .map(Transition::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
            
            // Transizioni entranti (toStatus)
            List<Transition> incomingTransitions = transitionRepository.findByToStatus(workflowStatus);
            removedTransitionIds.addAll(incomingTransitions.stream()
                    .map(Transition::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
        }
        
        // Analizza le StatusOwnerPermissions che verranno rimosse
        List<StatusRemovalImpactDto.PermissionImpact> statusOwnerPermissions = 
                analyzeStatusOwnerPermissionImpacts(allItemTypeSetsUsingWorkflow, removedStatusIds);
        
        // Analizza le ExecutorPermissions per le transizioni che verranno rimosse
        List<TransitionRemovalImpactDto.PermissionImpact> executorPermissionImpacts = 
                !removedTransitionIds.isEmpty() 
                    ? analyzeExecutorPermissionImpacts(allItemTypeSetsUsingWorkflow, removedTransitionIds)
                    : new ArrayList<>();
        
        // Analizza le FieldStatusPermissions (EDITORS/VIEWERS) per i WorkflowStatus rimossi
        List<StatusRemovalImpactDto.FieldStatusPermissionImpact> fieldStatusPermissions = 
                analyzeFieldStatusPermissionImpacts(allItemTypeSetsUsingWorkflow, removedStatusIds, tenant);
        
        // Converti TransitionRemovalImpactDto.PermissionImpact in StatusRemovalImpactDto.ExecutorPermissionImpact
        List<StatusRemovalImpactDto.ExecutorPermissionImpact> executorPermissions = executorPermissionImpacts.stream()
                .map(transitionImpact -> {
                    // Converti ProjectRoleInfo da TransitionRemovalImpactDto a StatusRemovalImpactDto
                    List<StatusRemovalImpactDto.ProjectRoleInfo> projectRoles = transitionImpact.getProjectAssignedRoles() != null
                            ? transitionImpact.getProjectAssignedRoles().stream()
                                    .map(pr -> StatusRemovalImpactDto.ProjectRoleInfo.builder()
                                            .projectId(pr.getProjectId())
                                            .projectName(pr.getProjectName())
                                            .roles(pr.getRoles())
                                            .build())
                                    .collect(Collectors.toList())
                            : new ArrayList<>();
                    
                    return StatusRemovalImpactDto.ExecutorPermissionImpact.builder()
                            .permissionId(transitionImpact.getPermissionId())
                            .permissionType(transitionImpact.getPermissionType())
                            .itemTypeSetId(transitionImpact.getItemTypeSetId())
                            .itemTypeSetName(transitionImpact.getItemTypeSetName())
                            .projectId(transitionImpact.getProjectId())
                            .projectName(transitionImpact.getProjectName())
                            .transitionId(transitionImpact.getTransitionId())
                            .transitionName(transitionImpact.getTransitionName())
                            .fromStatusName(transitionImpact.getFromStatusName())
                            .toStatusName(transitionImpact.getToStatusName())
                            // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                            .grantId(transitionImpact.getGrantId())
                            .grantName(transitionImpact.getGrantName())
                            .assignedRoles(transitionImpact.getAssignedRoles()) // Solo ruoli globali
                            .projectAssignedRoles(projectRoles) // Ruoli di progetto separati
                            .hasAssignments(transitionImpact.isHasAssignments())
                            .transitionIdMatch(transitionImpact.getTransitionIdMatch())
                            .transitionNameMatch(transitionImpact.getTransitionNameMatch())
                            .canBePreserved(transitionImpact.isCanBePreserved())
                            .defaultPreserve(transitionImpact.isDefaultPreserve())
                            .projectGrants(transitionImpact.getProjectGrants().stream()
                                    .map(pg -> StatusRemovalImpactDto.ProjectGrantInfo.builder()
                                            .projectId(pg.getProjectId())
                                            .projectName(pg.getProjectName())
                                            // RIMOSSO: roleId - ItemTypeSetRole eliminata
                                            .build())
                                    .collect(Collectors.toList()))
                            .build();
                })
                .collect(Collectors.toList());
        
        // Calcola gli ItemTypeSet che hanno effettivamente impatti (status owner, executor o field status permissions)
        Set<Long> itemTypeSetIdsWithImpact = new HashSet<>();
        itemTypeSetIdsWithImpact.addAll(statusOwnerPermissions.stream()
                .map(StatusRemovalImpactDto.PermissionImpact::getItemTypeSetId)
                .collect(Collectors.toSet()));
        itemTypeSetIdsWithImpact.addAll(executorPermissions.stream()
                .map(StatusRemovalImpactDto.ExecutorPermissionImpact::getItemTypeSetId)
                .collect(Collectors.toSet()));
        itemTypeSetIdsWithImpact.addAll(fieldStatusPermissions.stream()
                .map(StatusRemovalImpactDto.FieldStatusPermissionImpact::getItemTypeSetId)
                .collect(Collectors.toSet()));
        
        List<ItemTypeSet> affectedItemTypeSets = allItemTypeSetsUsingWorkflow.stream()
                .filter(its -> itemTypeSetIdsWithImpact.contains(its.getId()))
                .collect(Collectors.toList());
        
        // Calcola le statistiche
        int totalStatusOwnerPermissions = statusOwnerPermissions.size();
        int totalExecutorPermissions = executorPermissions.size();
        int totalFieldStatusPermissions = fieldStatusPermissions.size();
        // Calcola totalRoleAssignments: ruoli globali + ruoli di progetto
        int totalGlobalRoles = statusOwnerPermissions.stream()
                .mapToInt(perm -> perm.getAssignedRoles() != null ? perm.getAssignedRoles().size() : 0)
                .sum() + executorPermissions.stream()
                .mapToInt(perm -> perm.getAssignedRoles() != null ? perm.getAssignedRoles().size() : 0)
                .sum() + fieldStatusPermissions.stream()
                .mapToInt(perm -> perm.getAssignedRoles() != null ? perm.getAssignedRoles().size() : 0)
                .sum();
        
        int totalProjectRoles = statusOwnerPermissions.stream()
                .mapToInt(perm -> perm.getProjectAssignedRoles() != null 
                        ? perm.getProjectAssignedRoles().stream()
                                .mapToInt(pr -> pr.getRoles() != null ? pr.getRoles().size() : 0)
                                .sum()
                        : 0)
                .sum() + executorPermissions.stream()
                .mapToInt(perm -> perm.getProjectAssignedRoles() != null 
                        ? perm.getProjectAssignedRoles().stream()
                                .mapToInt(pr -> pr.getRoles() != null ? pr.getRoles().size() : 0)
                                .sum()
                        : 0)
                .sum() + fieldStatusPermissions.stream()
                .mapToInt(perm -> perm.getProjectAssignedRoles() != null 
                        ? perm.getProjectAssignedRoles().stream()
                                .mapToInt(pr -> pr.getRoles() != null ? pr.getRoles().size() : 0)
                                .sum()
                        : 0)
                .sum();
        
        int totalRoleAssignments = totalGlobalRoles + totalProjectRoles;
        // Calcola totalGrantAssignments: conta i grant globali E di progetto (solo se hanno assegnazioni)
        int totalGlobalGrants = (int) statusOwnerPermissions.stream()
                .filter(perm -> perm.getGrantId() != null)
                .count() + (int) executorPermissions.stream()
                .filter(perm -> perm.getGrantId() != null)
                .count() + (int) fieldStatusPermissions.stream()
                .filter(perm -> perm.getGrantId() != null)
                .count();
        
        // Conta anche i grant di progetto
        int totalProjectGrants = statusOwnerPermissions.stream()
                .mapToInt(perm -> perm.getProjectGrants() != null ? perm.getProjectGrants().size() : 0)
                .sum() + executorPermissions.stream()
                .mapToInt(perm -> perm.getProjectGrants() != null ? perm.getProjectGrants().size() : 0)
                .sum() + fieldStatusPermissions.stream()
                .mapToInt(perm -> perm.getProjectGrants() != null ? perm.getProjectGrants().size() : 0)
                .sum();
        
        int totalGrantAssignments = totalGlobalGrants + totalProjectGrants;
        
        // Ottieni i nomi degli Status rimossi
        List<String> removedStatusNames = getStatusNames(removedStatusIds, tenant);
        
        // Ottieni i nomi delle Transizioni rimosse
        List<String> removedTransitionNames = getTransitionNames(removedTransitionIds, tenant);
        
        return StatusRemovalImpactDto.builder()
                .workflowId(workflowId)
                .workflowName(workflow.getName())
                .removedStatusIds(new ArrayList<>(removedStatusIds))
                .removedStatusNames(removedStatusNames)
                .removedTransitionIds(new ArrayList<>(removedTransitionIds))
                .removedTransitionNames(removedTransitionNames)
                .affectedItemTypeSets(mapItemTypeSetImpactsWithAggregatesForStatus(
                        affectedItemTypeSets,
                        statusOwnerPermissions,
                        executorPermissions,
                        fieldStatusPermissions))
                .statusOwnerPermissions(statusOwnerPermissions)
                .executorPermissions(executorPermissions)
                .fieldStatusPermissions(fieldStatusPermissions)
                .totalAffectedItemTypeSets(affectedItemTypeSets.size()) // Solo quelli con impatti effettivi
                .totalStatusOwnerPermissions(totalStatusOwnerPermissions)
                .totalExecutorPermissions(totalExecutorPermissions)
                .totalFieldStatusPermissions(totalFieldStatusPermissions)
                .totalGrantAssignments(totalGrantAssignments)
                .totalRoleAssignments(totalRoleAssignments)
                .build();
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
     * Analizza le StatusOwnerPermissions che verranno rimosse
     */
    private List<StatusRemovalImpactDto.PermissionImpact> analyzeStatusOwnerPermissionImpacts(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedStatusIds
    ) {
        List<StatusRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                // Trova le StatusOwnerPermissions per gli Status rimossi
                List<StatusOwnerPermission> permissions = statusOwnerPermissionRepository
                        .findByItemTypeConfigurationAndWorkflowStatusIdIn(config, removedStatusIds);
                
                for (StatusOwnerPermission permission : permissions) {
                    WorkflowStatus workflowStatus = permission.getWorkflowStatus();
                    
                    // Recupera ruoli globali da PermissionAssignment
                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "StatusOwnerPermission", permission.getId(), itemTypeSet.getTenant());
                    
                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());
                    
                    // Recupera Grant globale se presente
                    Long globalGrantId = null;
                    String globalGrantName = null;
                    if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                        Grant grant = assignmentOpt.get().getGrant();
                        globalGrantId = grant.getId();
                        globalGrantName = grant.getRole() != null 
                                ? grant.getRole().getName() 
                                : "Grant globale";
                    }
                    
                    // Grant e ruoli di progetto - recupera da ProjectPermissionAssignmentService
                    List<StatusRemovalImpactDto.ProjectGrantInfo> projectGrantsList = new ArrayList<>();
                    List<StatusRemovalImpactDto.ProjectRoleInfo> projectRolesList = new ArrayList<>();
                    // Se è un ItemTypeSet di progetto, controlla solo quel progetto
                    if (itemTypeSet.getProject() != null) {
                        Optional<PermissionAssignment> projectAssignmentOpt = 
                                projectPermissionAssignmentService.getProjectAssignment(
                                        "StatusOwnerPermission", permission.getId(), 
                                        itemTypeSet.getProject().getId(), itemTypeSet.getTenant());
                        if (projectAssignmentOpt.isPresent()) {
                            PermissionAssignment projectAssignment = projectAssignmentOpt.get();
                            // Aggiungi grant di progetto se presente
                            if (projectAssignment.getGrant() != null) {
                                projectGrantsList.add(StatusRemovalImpactDto.ProjectGrantInfo.builder()
                                        .projectId(itemTypeSet.getProject().getId())
                                        .projectName(itemTypeSet.getProject().getName())
                                        .build());
                            }
                            // IMPORTANTE: Separare i ruoli di progetto dai ruoli globali
                            Set<Role> projectRoles = projectAssignment.getRoles();
                            if (projectRoles != null && !projectRoles.isEmpty()) {
                                List<String> projectRoleNames = projectRoles.stream()
                                        .map(Role::getName)
                                        .collect(Collectors.toList());
                                projectRolesList.add(StatusRemovalImpactDto.ProjectRoleInfo.builder()
                                        .projectId(itemTypeSet.getProject().getId())
                                        .projectName(itemTypeSet.getProject().getName())
                                        .roles(projectRoleNames)
                                        .build());
                            }
                        }
                    } else {
                        // Se è un ItemTypeSet globale, controlla tutti i progetti associati
                        for (Project project : itemTypeSet.getProjectsAssociation()) {
                            Optional<PermissionAssignment> projectAssignmentOpt = 
                                    projectPermissionAssignmentService.getProjectAssignment(
                                            "StatusOwnerPermission", permission.getId(), 
                                            project.getId(), itemTypeSet.getTenant());
                            if (projectAssignmentOpt.isPresent()) {
                                PermissionAssignment projectAssignment = projectAssignmentOpt.get();
                                // Aggiungi grant di progetto se presente
                                if (projectAssignment.getGrant() != null) {
                                    projectGrantsList.add(StatusRemovalImpactDto.ProjectGrantInfo.builder()
                                            .projectId(project.getId())
                                            .projectName(project.getName())
                                            .build());
                                }
                                // IMPORTANTE: Separare i ruoli di progetto dai ruoli globali
                                Set<Role> projectRoles = projectAssignment.getRoles();
                                if (projectRoles != null && !projectRoles.isEmpty()) {
                                    List<String> projectRoleNames = projectRoles.stream()
                                            .map(Role::getName)
                                            .collect(Collectors.toList());
                                    projectRolesList.add(StatusRemovalImpactDto.ProjectRoleInfo.builder()
                                            .projectId(project.getId())
                                            .projectName(project.getName())
                                            .roles(projectRoleNames)
                                            .build());
                                }
                            }
                        }
                    }
                    
                    // Calcola hasAssignments: true se ha ruoli O grant (globali o di progetto)
                    boolean hasGlobalRoles = !assignedRoles.isEmpty();
                    boolean hasProjectRoles = !projectRolesList.isEmpty();
                    boolean hasGlobalGrant = globalGrantId != null;
                    boolean hasProjectGrant = !projectGrantsList.isEmpty();
                    boolean hasAssignments = hasGlobalRoles || hasProjectRoles || hasGlobalGrant || hasProjectGrant;
                    
                    // Solo se ha assegnazioni (ruoli o grant)
                    if (hasAssignments) {
                        // Per Status, canBePreserved è difficile da calcolare (dovrebbe verificare se esiste un altro Status equivalente)
                        // Per ora impostiamo a false
                        boolean canBePreserved = false;
                        boolean defaultPreserve = false;
                        
                        impacts.add(StatusRemovalImpactDto.PermissionImpact.builder()
                                .permissionId(permission.getId())
                                .permissionType("STATUS_OWNERS")
                                .itemTypeSetId(itemTypeSet.getId())
                                .itemTypeSetName(itemTypeSet.getName())
                                .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                                .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                                .workflowStatusId(workflowStatus.getId())
                                .workflowStatusName(workflowStatus.getStatus().getName())
                                .statusName(workflowStatus.getStatus().getName())
                                .statusCategory(workflowStatus.getStatusCategory().toString())
                                // RIMOSSO: roleId - ItemTypeSetRole eliminata
                                .grantId(globalGrantId)
                                .grantName(globalGrantName)
                                .assignedRoles(assignedRoles) // Solo ruoli globali
                                .projectAssignedRoles(projectRolesList) // Ruoli di progetto separati
                                .hasAssignments(true)
                                .statusId(workflowStatus.getStatus().getId())
                                .canBePreserved(canBePreserved)
                                .defaultPreserve(defaultPreserve)
                                .projectGrants(projectGrantsList)
                                .build());
                    }
                }
            }
        }
        
        return impacts;
    }
    
    /**
     * Analizza le FieldStatusPermissions (EDITORS/VIEWERS) che verranno rimosse quando si rimuovono WorkflowStatus
     */
    private List<StatusRemovalImpactDto.FieldStatusPermissionImpact> analyzeFieldStatusPermissionImpacts(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedStatusIds,
            Tenant tenant
    ) {
        List<StatusRemovalImpactDto.FieldStatusPermissionImpact> impacts = new ArrayList<>();
        
        // Crea un Set degli ID degli Status (non WorkflowStatus) che verranno rimossi
        // Per trovare le permission, dobbiamo cercare per Status.id, non WorkflowStatus.id
        Set<Long> removedStatusEntityIds = new HashSet<>();
        // Carica direttamente i WorkflowStatus rimossi dal repository
        for (Long workflowStatusId : removedStatusIds) {
            WorkflowStatus ws = workflowStatusRepository.findByWorkflowStatusIdAndTenant(workflowStatusId, tenant).orElse(null);
            if (ws != null && ws.getStatus() != null) {
                removedStatusEntityIds.add(ws.getStatus().getId());
            }
        }
        
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                // Trova tutte le FieldStatusPermissions per questa configurazione
                List<FieldStatusPermission> permissions = fieldStatusPermissionRepository
                        .findAllByItemTypeConfiguration(config);
                
                for (FieldStatusPermission permission : permissions) {
                    WorkflowStatus workflowStatus = permission.getWorkflowStatus();
                    if (workflowStatus == null || workflowStatus.getStatus() == null) {
                        continue;
                    }
                    
                    // Verifica se questo WorkflowStatus verrà rimosso
                    Long statusEntityId = workflowStatus.getStatus().getId();
                    if (!removedStatusEntityIds.contains(statusEntityId)) {
                        continue;
                    }
                    
                    // Verifica anche che il WorkflowStatus.id sia nella lista dei rimossi
                    if (!removedStatusIds.contains(workflowStatus.getId())) {
                        continue;
                    }
                    
                    // Recupera ruoli globali da PermissionAssignment
                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "FieldStatusPermission", permission.getId(), itemTypeSet.getTenant());
                    
                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());
                    
                    // Recupera Grant globale se presente
                    Long globalGrantId = null;
                    String globalGrantName = null;
                    if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                        Grant grant = assignmentOpt.get().getGrant();
                        globalGrantId = grant.getId();
                        globalGrantName = grant.getRole() != null 
                                ? grant.getRole().getName() 
                                : "Grant globale";
                    }
                    
                    // Grant e ruoli di progetto - recupera da ProjectPermissionAssignmentService
                    List<StatusRemovalImpactDto.ProjectGrantInfo> projectGrantsList = new ArrayList<>();
                    List<StatusRemovalImpactDto.ProjectRoleInfo> projectRolesList = new ArrayList<>();
                    // Se è un ItemTypeSet di progetto, controlla solo quel progetto
                    if (itemTypeSet.getProject() != null) {
                        Optional<PermissionAssignment> projectAssignmentOpt = 
                                projectPermissionAssignmentService.getProjectAssignment(
                                        "FieldStatusPermission", permission.getId(), 
                                        itemTypeSet.getProject().getId(), itemTypeSet.getTenant());
                        if (projectAssignmentOpt.isPresent()) {
                            PermissionAssignment projectAssignment = projectAssignmentOpt.get();
                            // Aggiungi grant di progetto se presente
                            if (projectAssignment.getGrant() != null) {
                                projectGrantsList.add(StatusRemovalImpactDto.ProjectGrantInfo.builder()
                                        .projectId(itemTypeSet.getProject().getId())
                                        .projectName(itemTypeSet.getProject().getName())
                                        .build());
                            }
                            // IMPORTANTE: Separare i ruoli di progetto dai ruoli globali
                            Set<Role> projectRoles = projectAssignment.getRoles();
                            if (projectRoles != null && !projectRoles.isEmpty()) {
                                List<String> projectRoleNames = projectRoles.stream()
                                        .map(Role::getName)
                                        .collect(Collectors.toList());
                                projectRolesList.add(StatusRemovalImpactDto.ProjectRoleInfo.builder()
                                        .projectId(itemTypeSet.getProject().getId())
                                        .projectName(itemTypeSet.getProject().getName())
                                        .roles(projectRoleNames)
                                        .build());
                            }
                        }
                    } else {
                        // Se è un ItemTypeSet globale, controlla tutti i progetti associati
                        for (Project project : itemTypeSet.getProjectsAssociation()) {
                            Optional<PermissionAssignment> projectAssignmentOpt = 
                                    projectPermissionAssignmentService.getProjectAssignment(
                                            "FieldStatusPermission", permission.getId(), 
                                            project.getId(), itemTypeSet.getTenant());
                            if (projectAssignmentOpt.isPresent()) {
                                PermissionAssignment projectAssignment = projectAssignmentOpt.get();
                                // Aggiungi grant di progetto se presente
                                if (projectAssignment.getGrant() != null) {
                                    projectGrantsList.add(StatusRemovalImpactDto.ProjectGrantInfo.builder()
                                            .projectId(project.getId())
                                            .projectName(project.getName())
                                            .build());
                                }
                                // IMPORTANTE: Separare i ruoli di progetto dai ruoli globali
                                Set<Role> projectRoles = projectAssignment.getRoles();
                                if (projectRoles != null && !projectRoles.isEmpty()) {
                                    List<String> projectRoleNames = projectRoles.stream()
                                            .map(Role::getName)
                                            .collect(Collectors.toList());
                                    projectRolesList.add(StatusRemovalImpactDto.ProjectRoleInfo.builder()
                                            .projectId(project.getId())
                                            .projectName(project.getName())
                                            .roles(projectRoleNames)
                                            .build());
                                }
                            }
                        }
                    }
                    
                    // Rimuovi la logica ItemTypeSetRole (obsoleta)
                    // Trova la FieldConfiguration nel FieldSet (per info, non più per ItemTypeSetRole)
                    Field field = permission.getField();
                    
                    // Calcola hasAssignments: true se ha ruoli O grant (globali o di progetto)
                    boolean hasGlobalRoles = !assignedRoles.isEmpty();
                    boolean hasProjectRoles = !projectRolesList.isEmpty();
                    boolean hasGlobalGrant = globalGrantId != null;
                    boolean hasProjectGrant = !projectGrantsList.isEmpty();
                    boolean hasAssignments = hasGlobalRoles || hasProjectRoles || hasGlobalGrant || hasProjectGrant;
                    
                    // Solo se ha assegnazioni (ruoli o grant)
                    if (hasAssignments) {
                        // Per FieldStatus, canBePreserved è false perché il WorkflowStatus viene rimosso
                        boolean canBePreserved = false;
                        boolean defaultPreserve = false;
                        
                        String permissionType = permission.getPermissionType() == FieldStatusPermission.PermissionType.EDITORS
                                ? "FIELD_EDITORS"
                                : "FIELD_VIEWERS";

                        impacts.add(StatusRemovalImpactDto.FieldStatusPermissionImpact.builder()
                                .permissionId(permission.getId())
                                .permissionType(permissionType)
                                .itemTypeSetId(itemTypeSet.getId())
                                .itemTypeSetName(itemTypeSet.getName())
                                .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                                .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                                .fieldId(field.getId())
                                .fieldName(field.getName())
                                .workflowStatusId(workflowStatus.getId())
                                .workflowStatusName(workflowStatus.getStatus().getName())
                                .statusName(workflowStatus.getStatus().getName())
                                // RIMOSSO: roleId - ItemTypeSetRole eliminata
                                // RIMOSSO: roleName - ItemTypeSetRole eliminata
                                .grantId(globalGrantId)
                                .grantName(globalGrantName)
                                .assignedRoles(assignedRoles) // Solo ruoli globali
                                .projectAssignedRoles(projectRolesList) // Ruoli di progetto separati
                                .hasAssignments(true)
                                .canBePreserved(canBePreserved)
                                .defaultPreserve(defaultPreserve)
                                .projectGrants(projectGrantsList)
                                .build());
                    }
                }
            }
        }
        
        return impacts;
    }
    
    /**
     * Ottiene i nomi degli Status rimossi
     */
    private List<String> getStatusNames(Set<Long> statusIds, Tenant tenant) {
        return statusIds.stream()
                .map(id -> {
                    try {
                        WorkflowStatus workflowStatus = workflowStatusRepository.findById(id).orElse(null);
                        if (workflowStatus == null) {
                            return "Status " + id;
                        }
                        return workflowStatus.getStatus().getName();
                    } catch (Exception e) {
                        return "Status " + id;
                    }
                })
                .collect(Collectors.toList());
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
    
    /**
     * Mappa gli ItemTypeSet in impatti per Transition
     */
    private List<TransitionRemovalImpactDto.ItemTypeSetImpact> mapItemTypeSetImpacts(
            List<ItemTypeSet> itemTypeSets,
            List<TransitionRemovalImpactDto.PermissionImpact> executorPermissions,
            List<TransitionRemovalImpactDto.StatusOwnerPermissionImpact> statusOwnerPermissions,
            List<TransitionRemovalImpactDto.FieldStatusPermissionImpact> fieldStatusPermissions
    ) {
        return itemTypeSets.stream()
                .map(its -> {
                    Long itemTypeSetId = its.getId();

                    List<TransitionRemovalImpactDto.PermissionImpact> itsExecutorPermissions = executorPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList());

                    List<TransitionRemovalImpactDto.StatusOwnerPermissionImpact> itsStatusOwnerPermissions = statusOwnerPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList());

                    List<TransitionRemovalImpactDto.FieldStatusPermissionImpact> itsFieldStatusPermissions = fieldStatusPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList());

                    int totalPermissions = itsExecutorPermissions.size() + itsStatusOwnerPermissions.size() + itsFieldStatusPermissions.size();

                    int totalRoleAssignments = itsExecutorPermissions.stream()
                            .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                            .sum()
                            + itsStatusOwnerPermissions.stream()
                            .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                            .sum()
                            + itsFieldStatusPermissions.stream()
                            .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                            .sum();

                    int totalGlobalGrants = (int) itsExecutorPermissions.stream()
                            .filter(p -> p.getGrantId() != null)
                            .count()
                            + (int) itsStatusOwnerPermissions.stream()
                            .filter(p -> p.getGrantId() != null)
                            .count()
                            + (int) itsFieldStatusPermissions.stream()
                            .filter(p -> p.getGrantId() != null)
                            .count();

                    Map<Long, Integer> projectGrantsCount = new HashMap<>();

                    for (TransitionRemovalImpactDto.StatusOwnerPermissionImpact perm : itsStatusOwnerPermissions) {
                        if (perm.getProjectGrants() != null) {
                            for (TransitionRemovalImpactDto.ProjectGrantInfo pg : perm.getProjectGrants()) {
                                if (pg.getProjectId() != null) {
                                    projectGrantsCount.merge(pg.getProjectId(), 1, Integer::sum);
                                }
                            }
                        }
                    }

                    for (TransitionRemovalImpactDto.PermissionImpact perm : itsExecutorPermissions) {
                        if (perm.getProjectGrants() != null) {
                            for (TransitionRemovalImpactDto.ProjectGrantInfo pg : perm.getProjectGrants()) {
                                if (pg.getProjectId() != null) {
                                    projectGrantsCount.merge(pg.getProjectId(), 1, Integer::sum);
                                }
                            }
                        }
                    }

                    for (TransitionRemovalImpactDto.FieldStatusPermissionImpact perm : itsFieldStatusPermissions) {
                        if (perm.getProjectGrants() != null) {
                            for (TransitionRemovalImpactDto.ProjectGrantInfo pg : perm.getProjectGrants()) {
                                if (pg.getProjectId() != null) {
                                    projectGrantsCount.merge(pg.getProjectId(), 1, Integer::sum);
                                }
                            }
                        }
                    }

                    int totalProjectGrants = projectGrantsCount.values().stream().mapToInt(Integer::intValue).sum();

                    List<TransitionRemovalImpactDto.ProjectImpact> projectImpacts = projectGrantsCount.entrySet().stream()
                            .map(entry -> {
                                Long projectId = entry.getKey();
                                String projectName;
                                if (its.getProject() != null && its.getProject().getId().equals(projectId)) {
                                    projectName = its.getProject().getName();
                                } else {
                                    projectName = its.getProjectsAssociation().stream()
                                            .filter(project -> project.getId().equals(projectId))
                                            .findFirst()
                                            .map(Project::getName)
                                            .orElse("Progetto " + projectId);
                                }
                                return TransitionRemovalImpactDto.ProjectImpact.builder()
                                        .projectId(projectId)
                                        .projectName(projectName)
                                        .projectGrantsCount(entry.getValue())
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return TransitionRemovalImpactDto.ItemTypeSetImpact.builder()
                            .itemTypeSetId(itemTypeSetId)
                            .itemTypeSetName(its.getName())
                            .projectId(its.getProject() != null ? its.getProject().getId() : null)
                            .projectName(its.getProject() != null ? its.getProject().getName() : null)
                            .totalPermissions(totalPermissions)
                            .totalRoleAssignments(totalRoleAssignments)
                            .totalGlobalGrants(totalGlobalGrants)
                            .totalProjectGrants(totalProjectGrants)
                            .projectImpacts(projectImpacts)
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Mappa gli ItemTypeSet in impatti per Status
     */
    /**
     * Mappa gli ItemTypeSet con informazioni aggregate (permission, ruoli, grant globali e di progetto)
     */
    private List<StatusRemovalImpactDto.ItemTypeSetImpact> mapItemTypeSetImpactsWithAggregatesForStatus(
            List<ItemTypeSet> itemTypeSets,
            List<StatusRemovalImpactDto.PermissionImpact> statusOwnerPermissions,
            List<StatusRemovalImpactDto.ExecutorPermissionImpact> executorPermissions,
            List<StatusRemovalImpactDto.FieldStatusPermissionImpact> fieldStatusPermissions
    ) {
        return itemTypeSets.stream()
                .map(its -> {
                    Long itemTypeSetId = its.getId();
                    
                    // Filtra permission per questo ItemTypeSet
                    List<StatusRemovalImpactDto.PermissionImpact> itsStatusOwnerPermissions = statusOwnerPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList());
                    
                    List<StatusRemovalImpactDto.ExecutorPermissionImpact> itsExecutorPermissions = executorPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList());
                    
                    List<StatusRemovalImpactDto.FieldStatusPermissionImpact> itsFieldStatusPermissions = fieldStatusPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList());
                    
                    // Calcola totali
                    int totalPermissions = itsStatusOwnerPermissions.size() + itsExecutorPermissions.size() + itsFieldStatusPermissions.size();
                    
                    int totalRoleAssignments = itsStatusOwnerPermissions.stream()
                            .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                            .sum() + itsExecutorPermissions.stream()
                            .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                            .sum() + itsFieldStatusPermissions.stream()
                            .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                            .sum();
                    
                    // Calcola grant globali (conteggio di permission con grantId != null)
                    int totalGlobalGrants = (int) itsStatusOwnerPermissions.stream()
                            .filter(p -> p.getGrantId() != null)
                            .count() + (int) itsExecutorPermissions.stream()
                            .filter(p -> p.getGrantId() != null)
                            .count() + (int) itsFieldStatusPermissions.stream()
                            .filter(p -> p.getGrantId() != null)
                            .count();
                    
                    // Calcola grant di progetto per questo ItemTypeSet
                    // Raccoglie le grant di progetto da tutte le permission (già popolate nel DTO)
                    Map<Long, Integer> projectGrantsCount = new HashMap<>();
                    
                    // Da StatusOwnerPermissions
                    for (StatusRemovalImpactDto.PermissionImpact perm : itsStatusOwnerPermissions) {
                        if (perm.getProjectGrants() != null) {
                            for (StatusRemovalImpactDto.ProjectGrantInfo pg : perm.getProjectGrants()) {
                                projectGrantsCount.merge(pg.getProjectId(), 1, Integer::sum);
                            }
                        }
                    }
                    
                    // Da ExecutorPermissions
                    for (StatusRemovalImpactDto.ExecutorPermissionImpact perm : itsExecutorPermissions) {
                        if (perm.getProjectGrants() != null) {
                            for (StatusRemovalImpactDto.ProjectGrantInfo pg : perm.getProjectGrants()) {
                                projectGrantsCount.merge(pg.getProjectId(), 1, Integer::sum);
                            }
                        }
                    }
                    
                    // Da FieldStatusPermissions
                    for (StatusRemovalImpactDto.FieldStatusPermissionImpact perm : itsFieldStatusPermissions) {
                        if (perm.getProjectGrants() != null) {
                            for (StatusRemovalImpactDto.ProjectGrantInfo pg : perm.getProjectGrants()) {
                                projectGrantsCount.merge(pg.getProjectId(), 1, Integer::sum);
                            }
                        }
                    }
                    
                    int totalProjectGrants = projectGrantsCount.values().stream().mapToInt(Integer::intValue).sum();
                    
                    // Crea projectImpacts
                    List<StatusRemovalImpactDto.ProjectImpact> projectImpacts = projectGrantsCount.entrySet().stream()
                            .map(e -> {
                                // Trova il nome del progetto
                                String projectName = its.getProject() != null && its.getProject().getId().equals(e.getKey())
                                        ? its.getProject().getName()
                                        : its.getProjectsAssociation().stream()
                                                .filter(p -> p.getId().equals(e.getKey()))
                                                .findFirst()
                                                .map(Project::getName)
                                                .orElse("Progetto " + e.getKey());
                                return StatusRemovalImpactDto.ProjectImpact.builder()
                                        .projectId(e.getKey())
                                        .projectName(projectName)
                                        .projectGrantsCount(e.getValue())
                                        .build();
                            })
                            .collect(Collectors.toList());
                    
                    return StatusRemovalImpactDto.ItemTypeSetImpact.builder()
                            .itemTypeSetId(itemTypeSetId)
                            .itemTypeSetName(its.getName())
                            .projectId(its.getProject() != null ? its.getProject().getId() : null)
                            .projectName(its.getProject() != null ? its.getProject().getName() : null)
                            .totalPermissions(totalPermissions)
                            .totalRoleAssignments(totalRoleAssignments)
                            .totalGlobalGrants(totalGlobalGrants)
                            .totalProjectGrants(totalProjectGrants)
                            .projectImpacts(projectImpacts)
                            .build();
                })
                .collect(Collectors.toList());
    }
}


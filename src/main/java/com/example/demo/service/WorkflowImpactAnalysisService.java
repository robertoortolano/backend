package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
// RIMOSSO: ItemTypeSetRoleType eliminato - ItemTypeSetRole eliminata
import com.example.demo.exception.ApiException;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analyzing the impact of workflow changes (status/transition removal)
 * Extracted from WorkflowService to improve maintainability and testability
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkflowImpactAnalysisService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final TransitionRepository transitionRepository;
    private final ItemTypeSetRepository itemTypeSetRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final ItemTypeConfigurationLookup itemTypeConfigurationLookup;
    
    // Servizi per PermissionAssignment (nuova struttura)
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;

    /**
     * Analyzes the impact of removing workflow statuses
     */
    public StatusRemovalImpactDto analyzeStatusRemovalImpact(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedStatusIds
    ) {
        Workflow workflow = workflowRepository.findByIdAndTenant(workflowId, tenant)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        // Find all ItemTypeSets using this Workflow
        List<ItemTypeSet> allItemTypeSetsUsingWorkflow = findItemTypeSetsUsingWorkflow(workflowId, tenant);
        
        // Find all WorkflowStatuses that will be removed
        List<WorkflowStatus> removedWorkflowStatuses = workflow.getStatuses().stream()
                .filter(ws -> removedStatusIds.contains(ws.getId()))
                .collect(Collectors.toList());
        
        // Find all transitions that will be removed (incoming and outgoing from removed statuses)
        Set<Long> removedTransitionIds = new HashSet<>();
        for (WorkflowStatus workflowStatus : removedWorkflowStatuses) {
            // Outgoing transitions (fromStatus)
            List<Transition> outgoingTransitions = transitionRepository.findByFromStatusAndTenant(workflowStatus, tenant);
            removedTransitionIds.addAll(outgoingTransitions.stream()
                    .map(Transition::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
            
            // Incoming transitions (toStatus)
            List<Transition> incomingTransitions = transitionRepository.findByToStatusAndTenant(workflowStatus, tenant);
            removedTransitionIds.addAll(incomingTransitions.stream()
                    .map(Transition::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
        }
        
        // Analyze StatusOwnerPermissions that will be removed
        List<StatusRemovalImpactDto.PermissionImpact> statusOwnerPermissions = 
                analyzeStatusOwnerPermissionImpacts(allItemTypeSetsUsingWorkflow, removedStatusIds);
        
        // Analyze ExecutorPermissions for transitions that will be removed
        List<TransitionRemovalImpactDto.PermissionImpact> executorPermissionImpacts = 
                !removedTransitionIds.isEmpty() 
                    ? analyzeExecutorPermissionImpacts(allItemTypeSetsUsingWorkflow, removedTransitionIds)
                    : new ArrayList<>();
        
        // Analyze FieldStatusPermissions (EDITORS/VIEWERS) for removed WorkflowStatuses
        List<StatusRemovalImpactDto.FieldStatusPermissionImpact> fieldStatusPermissions = 
                analyzeFieldStatusPermissionImpacts(tenant, allItemTypeSetsUsingWorkflow, removedStatusIds);
        
        // Convert TransitionRemovalImpactDto.PermissionImpact to StatusRemovalImpactDto.ExecutorPermissionImpact
        List<StatusRemovalImpactDto.ExecutorPermissionImpact> executorPermissions = executorPermissionImpacts.stream()
                .map(transitionImpact -> StatusRemovalImpactDto.ExecutorPermissionImpact.builder()
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
                        .assignedRoles(transitionImpact.getAssignedRoles())
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
                        .build())
                .collect(Collectors.toList());
        
        // Calculate ItemTypeSets that actually have impacts (status owner, executor or field status permissions)
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
        
        // Calculate statistics using assignedRoles from DTO (already populated from PermissionAssignment)
        int totalStatusOwnerPermissions = statusOwnerPermissions.size();
        int totalExecutorPermissions = executorPermissions.size();
        int totalFieldStatusPermissions = fieldStatusPermissions.size();
        int totalRoleAssignments = statusOwnerPermissions.stream()
                .mapToInt(perm -> perm.getAssignedRoles() != null ? perm.getAssignedRoles().size() : 0)
                .sum() + executorPermissions.stream()
                .mapToInt(perm -> perm.getAssignedRoles() != null ? perm.getAssignedRoles().size() : 0)
                .sum() + fieldStatusPermissions.stream()
                .mapToInt(perm -> perm.getAssignedRoles() != null ? perm.getAssignedRoles().size() : 0)
                .sum();
        // Calculate totalGrantAssignments: count global grants (only if they have assignments)
        int totalGrantAssignments = (int) statusOwnerPermissions.stream()
                .filter(perm -> perm.getGrantId() != null)
                .count() + (int) executorPermissions.stream()
                .filter(perm -> perm.getGrantId() != null)
                .count() + (int) fieldStatusPermissions.stream()
                .filter(perm -> perm.getGrantId() != null)
                .count();
        
        // Get names of removed Statuses
        List<String> removedStatusNames = getStatusNames(removedStatusIds, tenant);
        
        // Get names of removed Transitions
        List<String> removedTransitionNames = getTransitionNames(removedTransitionIds, tenant);
        
        return StatusRemovalImpactDto.builder()
                .workflowId(workflowId)
                .workflowName(workflow.getName())
                .removedStatusIds(new ArrayList<>(removedStatusIds))
                .removedStatusNames(removedStatusNames)
                .removedTransitionIds(new ArrayList<>(removedTransitionIds))
                .removedTransitionNames(removedTransitionNames)
                .affectedItemTypeSets(mapItemTypeSetImpactsForStatus(affectedItemTypeSets))
                .statusOwnerPermissions(statusOwnerPermissions)
                .executorPermissions(executorPermissions)
                .fieldStatusPermissions(fieldStatusPermissions)
                .totalAffectedItemTypeSets(affectedItemTypeSets.size()) // Only those with actual impacts
                .totalStatusOwnerPermissions(totalStatusOwnerPermissions)
                .totalExecutorPermissions(totalExecutorPermissions)
                .totalFieldStatusPermissions(totalFieldStatusPermissions)
                .totalGrantAssignments(totalGrantAssignments)
                .totalRoleAssignments(totalRoleAssignments)
                .build();
    }

    /**
     * Analyzes the impact of removing transitions
     */
    public TransitionRemovalImpactDto analyzeTransitionRemovalImpact(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedTransitionIds
    ) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        if (!workflow.getTenant().equals(tenant)) {
            throw new ApiException("Workflow does not belong to tenant");
        }

        // Find all ItemTypeSets using this Workflow
        List<ItemTypeSet> allItemTypeSetsUsingWorkflow = findItemTypeSetsUsingWorkflow(workflowId, tenant);
        
        // Analyze ExecutorPermissions that will be removed
        List<TransitionRemovalImpactDto.PermissionImpact> executorPermissions = 
                analyzeExecutorPermissionImpacts(allItemTypeSetsUsingWorkflow, removedTransitionIds);
        
        // Calculate ItemTypeSets that actually have impacts
        Set<Long> itemTypeSetIdsWithImpact = executorPermissions.stream()
                .map(TransitionRemovalImpactDto.PermissionImpact::getItemTypeSetId)
                .collect(Collectors.toSet());
        
        List<ItemTypeSet> affectedItemTypeSets = allItemTypeSetsUsingWorkflow.stream()
                .filter(its -> itemTypeSetIdsWithImpact.contains(its.getId()))
                .collect(Collectors.toList());
        
        // Calculate statistics using assignedRoles from DTO (already populated from PermissionAssignment)
        int totalExecutorPermissions = executorPermissions.size();
        int totalRoleAssignments = executorPermissions.stream()
                .mapToInt(perm -> perm.getAssignedRoles() != null ? perm.getAssignedRoles().size() : 0)
                .sum();
        int totalGrantAssignments = (int) executorPermissions.stream()
                .filter(perm -> perm.getGrantId() != null)
                .count();
        
        // Get names of removed Transitions
        List<String> removedTransitionNames = getTransitionNames(removedTransitionIds, tenant);
        
        return TransitionRemovalImpactDto.builder()
                .workflowId(workflowId)
                .workflowName(workflow.getName())
                .removedTransitionIds(new ArrayList<>(removedTransitionIds))
                .removedTransitionNames(removedTransitionNames)
                .affectedItemTypeSets(mapItemTypeSetImpacts(affectedItemTypeSets))
                .executorPermissions(executorPermissions)
                .totalAffectedItemTypeSets(affectedItemTypeSets.size())
                .totalExecutorPermissions(totalExecutorPermissions)
                .totalGrantAssignments(totalGrantAssignments)
                .totalRoleAssignments(totalRoleAssignments)
                .build();
    }

    /**
     * Analyzes StatusOwnerPermission impacts
     */
    private List<StatusRemovalImpactDto.PermissionImpact> analyzeStatusOwnerPermissionImpacts(
            List<ItemTypeSet> itemTypeSets,
            Set<Long> removedStatusIds
    ) {
        List<StatusRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                // Find StatusOwnerPermissions for removed statuses
                List<StatusOwnerPermission> permissions = statusOwnerPermissionRepository
                        .findByItemTypeConfigurationAndWorkflowStatusIdIn(config, removedStatusIds);
                
                for (StatusOwnerPermission perm : permissions) {
                    WorkflowStatus workflowStatus = perm.getWorkflowStatus();
                    if (workflowStatus == null || !removedStatusIds.contains(workflowStatus.getId())) {
                        continue;
                    }
                    
                    // RIMOSSO: ItemTypeSetRole eliminata - recupera ruoli e grant da PermissionAssignment
                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "StatusOwnerPermission", perm.getId(), itemTypeSet.getTenant());
                    
                    // Collect role and grant information
                    List<String> assignedRoles = new ArrayList<>();
                    // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                    Long grantId = null;
                    String grantName = null;
                    List<StatusRemovalImpactDto.ProjectGrantInfo> projectGrants = new ArrayList<>();
                    
                    if (assignmentOpt.isPresent()) {
                        PermissionAssignment assignment = assignmentOpt.get();
                        // Recupera ruoli
                        for (Role role : assignment.getRoles()) {
                            // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata, non più necessari
                            assignedRoles.add(role.getName());
                        }
                        
                        // Recupera grant globale
                        if (assignment.getGrant() != null) {
                            Grant grant = assignment.getGrant();
                            grantId = grant.getId();
                            grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
                        }
                    }
                    
                    // Recupera grant di progetto da ProjectPermissionAssignmentService
                    // Se è un ItemTypeSet di progetto, controlla solo quel progetto
                    if (itemTypeSet.getProject() != null) {
                        Optional<ProjectPermissionAssignment> projectAssignmentOpt = 
                                projectPermissionAssignmentService.getProjectAssignment(
                                        "StatusOwnerPermission", perm.getId(), 
                                        itemTypeSet.getProject().getId(), itemTypeSet.getTenant());
                        if (projectAssignmentOpt.isPresent() && 
                                projectAssignmentOpt.get().getAssignment().getGrant() != null) {
                            projectGrants.add(StatusRemovalImpactDto.ProjectGrantInfo.builder()
                                    .projectId(itemTypeSet.getProject().getId())
                                    .projectName(itemTypeSet.getProject().getName())
                                    .build());
                        }
                    } else {
                        // Se è un ItemTypeSet globale, controlla tutti i progetti associati
                        for (Project project : itemTypeSet.getProjectsAssociation()) {
                            Optional<ProjectPermissionAssignment> projectAssignmentOpt = 
                                    projectPermissionAssignmentService.getProjectAssignment(
                                            "StatusOwnerPermission", perm.getId(), 
                                            project.getId(), itemTypeSet.getTenant());
                            if (projectAssignmentOpt.isPresent() && 
                                    projectAssignmentOpt.get().getAssignment().getGrant() != null) {
                                projectGrants.add(StatusRemovalImpactDto.ProjectGrantInfo.builder()
                                        .projectId(project.getId())
                                        .projectName(project.getName())
                                        .build());
                            }
                        }
                    }
                    
                    boolean hasAssignments = !assignedRoles.isEmpty() || grantId != null || !projectGrants.isEmpty();
                    boolean canBePreserved = hasAssignments; // Can preserve if has assignments
                    
                    impacts.add(StatusRemovalImpactDto.PermissionImpact.builder()
                            .permissionId(perm.getId())
                            .permissionType("STATUS_OWNER")
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .workflowStatusId(workflowStatus.getId())
                            .statusName(workflowStatus.getStatus().getName())
                            .statusCategory(workflowStatus.getStatusCategory() != null ? workflowStatus.getStatusCategory().name() : null)
                            // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                            .grantId(grantId)
                            .grantName(grantName)
                            .assignedRoles(assignedRoles)
                            .hasAssignments(hasAssignments)
                            .canBePreserved(canBePreserved)
                            .defaultPreserve(canBePreserved) // Default to preserve if can be preserved
                            .projectGrants(projectGrants)
                            .build());
                }
            }
        }
        
        return impacts;
    }

    /**
     * Analyzes FieldStatusPermission impacts (EDITORS/VIEWERS)
     */
    private List<StatusRemovalImpactDto.FieldStatusPermissionImpact> analyzeFieldStatusPermissionImpacts(
            Tenant tenant,
            List<ItemTypeSet> itemTypeSets,
            Set<Long> removedStatusIds
    ) {
        List<StatusRemovalImpactDto.FieldStatusPermissionImpact> impacts = new ArrayList<>();
        
        // Create a Set of Status IDs (not WorkflowStatus) that will be removed
        // To find permissions, we need to search by Status.id, not WorkflowStatus.id
        Set<Long> removedStatusEntityIds = new HashSet<>();
        // Load removed WorkflowStatuses directly from repository
        for (Long workflowStatusId : removedStatusIds) {
            WorkflowStatus ws = workflowStatusRepository.findByIdAndTenant(workflowStatusId, tenant).orElse(null);
            if (ws != null && ws.getStatus() != null) {
                removedStatusEntityIds.add(ws.getStatus().getId());
            }
        }
        
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                // Find all FieldStatusPermissions for this configuration
                List<FieldStatusPermission> permissions = fieldStatusPermissionRepository
                        .findAllByItemTypeConfiguration(config);
                
                for (FieldStatusPermission perm : permissions) {
                    WorkflowStatus workflowStatus = perm.getWorkflowStatus();
                    if (workflowStatus == null || workflowStatus.getStatus() == null) {
                        continue;
                    }
                    
                    // Verify if this WorkflowStatus will be removed
                    Long statusEntityId = workflowStatus.getStatus().getId();
                    if (!removedStatusEntityIds.contains(statusEntityId)) {
                        continue;
                    }
                    
                    // Also verify that WorkflowStatus.id is in the removed list
                    if (!removedStatusIds.contains(workflowStatus.getId())) {
                        continue;
                    }
                    
                    // Find FieldConfiguration in FieldSet
                    Field field = perm.getField();
                    final FieldConfiguration fieldConfig;
                    if (config.getFieldSet() != null && config.getFieldSet().getFieldSetEntries() != null) {
                        fieldConfig = config.getFieldSet().getFieldSetEntries().stream()
                                .map(FieldSetEntry::getFieldConfiguration)
                                .filter(fc -> fc.getField() != null && fc.getField().getId().equals(field.getId()))
                                .findFirst()
                                .orElse(null);
                    } else {
                        fieldConfig = null;
                    }
                    
                    if (workflowStatus == null || !removedStatusIds.contains(workflowStatus.getId()) || fieldConfig == null) {
                        continue;
                    }
                    
                    if (fieldConfig != null) {
                        // RIMOSSO: ItemTypeSetRole eliminata - recupera ruoli e grant da PermissionAssignment
                        Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                                "FieldStatusPermission", perm.getId(), itemTypeSet.getTenant());
                        
                        // Collect role and grant information
                        List<String> assignedRoles = new ArrayList<>();
                        // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                        Long grantId = null;
                        String grantName = null;
                        List<StatusRemovalImpactDto.ProjectGrantInfo> projectGrants = new ArrayList<>();
                        
                        if (assignmentOpt.isPresent()) {
                            PermissionAssignment assignment = assignmentOpt.get();
                            // Recupera ruoli
                            for (Role role : assignment.getRoles()) {
                                // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata, non più necessari
                                assignedRoles.add(role.getName());
                            }
                            
                            // Recupera grant globale
                            if (assignment.getGrant() != null) {
                                Grant grant = assignment.getGrant();
                                grantId = grant.getId();
                                grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
                            }
                        }
                        
                        // Recupera grant di progetto da ProjectPermissionAssignmentService
                        // Se è un ItemTypeSet di progetto, controlla solo quel progetto
                        if (itemTypeSet.getProject() != null) {
                            Optional<ProjectPermissionAssignment> projectAssignmentOpt = 
                                    projectPermissionAssignmentService.getProjectAssignment(
                                            "FieldStatusPermission", perm.getId(), 
                                            itemTypeSet.getProject().getId(), itemTypeSet.getTenant());
                            if (projectAssignmentOpt.isPresent() && 
                                    projectAssignmentOpt.get().getAssignment().getGrant() != null) {
                                projectGrants.add(StatusRemovalImpactDto.ProjectGrantInfo.builder()
                                        .projectId(itemTypeSet.getProject().getId())
                                        .projectName(itemTypeSet.getProject().getName())
                                        .build());
                            }
                        } else {
                            // Se è un ItemTypeSet globale, controlla tutti i progetti associati
                            for (Project project : itemTypeSet.getProjectsAssociation()) {
                                Optional<ProjectPermissionAssignment> projectAssignmentOpt = 
                                        projectPermissionAssignmentService.getProjectAssignment(
                                                "FieldStatusPermission", perm.getId(), 
                                                project.getId(), itemTypeSet.getTenant());
                                if (projectAssignmentOpt.isPresent() && 
                                        projectAssignmentOpt.get().getAssignment().getGrant() != null) {
                                    projectGrants.add(StatusRemovalImpactDto.ProjectGrantInfo.builder()
                                            .projectId(project.getId())
                                            .projectName(project.getName())
                                            .build());
                                }
                            }
                        }
                        
                        // Calculate hasAssignments: true if has roles OR grant
                        boolean hasRoles = !assignedRoles.isEmpty();
                        boolean hasGrant = grantId != null || !projectGrants.isEmpty();
                        boolean hasAssignments = hasRoles || hasGrant;
                        
                        // Only if has assignments (roles or grant)
                        if (hasAssignments) {
                            // For FieldStatus, canBePreserved is false because WorkflowStatus is being removed
                            boolean canBePreserved = false;
                            boolean defaultPreserve = false;
                            
                            impacts.add(StatusRemovalImpactDto.FieldStatusPermissionImpact.builder()
                                    .permissionId(perm.getId())
                                    .permissionType(perm.getPermissionType().toString()) // "EDITORS" or "VIEWERS"
                                    .itemTypeSetId(itemTypeSet.getId())
                                    .itemTypeSetName(itemTypeSet.getName())
                                    .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                                    .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                                    .fieldId(field.getId())
                                    .fieldName(field.getName())
                                    .workflowStatusId(workflowStatus.getId())
                                    .workflowStatusName(workflowStatus.getStatus().getName())
                                    .statusName(workflowStatus.getStatus().getName())
                                    // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                                    .grantId(grantId)
                                    .grantName(grantName)
                                    .assignedRoles(assignedRoles)
                                    .hasAssignments(true)
                                    .canBePreserved(canBePreserved)
                                    .defaultPreserve(defaultPreserve)
                                    .projectGrants(projectGrants)
                                    .build());
                        }
                    }
                }
            }
        }
        
        return impacts;
    }

    /**
     * Analyzes ExecutorPermission impacts
     */
    private List<TransitionRemovalImpactDto.PermissionImpact> analyzeExecutorPermissionImpacts(
            List<ItemTypeSet> itemTypeSets,
            Set<Long> removedTransitionIds
    ) {
        List<TransitionRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                // Find ExecutorPermissions for removed transitions
                List<ExecutorPermission> permissions = executorPermissionRepository
                        .findByItemTypeConfigurationAndTransitionIdIn(config, removedTransitionIds);
                
                for (ExecutorPermission perm : permissions) {
                    Transition transition = perm.getTransition();
                    if (transition == null || !removedTransitionIds.contains(transition.getId())) {
                        continue;
                    }
                    
                    // RIMOSSO: ItemTypeSetRole eliminata - recupera ruoli e grant da PermissionAssignment
                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "ExecutorPermission", perm.getId(), itemTypeSet.getTenant());
                    
                    // Collect role and grant information
                    List<String> assignedRoles = new ArrayList<>();
                    // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                    Long grantId = null;
                    String grantName = null;
                    List<TransitionRemovalImpactDto.ProjectGrantInfo> projectGrants = new ArrayList<>();
                    
                    if (assignmentOpt.isPresent()) {
                        PermissionAssignment assignment = assignmentOpt.get();
                        // Recupera ruoli
                        for (Role role : assignment.getRoles()) {
                            // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata, non più necessari
                            assignedRoles.add(role.getName());
                        }
                        
                        // Recupera grant globale
                        if (assignment.getGrant() != null) {
                            Grant grant = assignment.getGrant();
                            grantId = grant.getId();
                            grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
                        }
                    }
                    
                    // Recupera grant di progetto da ProjectPermissionAssignmentService
                    // Se è un ItemTypeSet di progetto, controlla solo quel progetto
                    if (itemTypeSet.getProject() != null) {
                        Optional<ProjectPermissionAssignment> projectAssignmentOpt = 
                                projectPermissionAssignmentService.getProjectAssignment(
                                        "ExecutorPermission", perm.getId(), 
                                        itemTypeSet.getProject().getId(), itemTypeSet.getTenant());
                        if (projectAssignmentOpt.isPresent() && 
                                projectAssignmentOpt.get().getAssignment().getGrant() != null) {
                            projectGrants.add(TransitionRemovalImpactDto.ProjectGrantInfo.builder()
                                    .projectId(itemTypeSet.getProject().getId())
                                    .projectName(itemTypeSet.getProject().getName())
                                    .build());
                        }
                    } else {
                        // Se è un ItemTypeSet globale, controlla tutti i progetti associati
                        for (Project project : itemTypeSet.getProjectsAssociation()) {
                            Optional<ProjectPermissionAssignment> projectAssignmentOpt = 
                                    projectPermissionAssignmentService.getProjectAssignment(
                                            "ExecutorPermission", perm.getId(), 
                                            project.getId(), itemTypeSet.getTenant());
                            if (projectAssignmentOpt.isPresent() && 
                                    projectAssignmentOpt.get().getAssignment().getGrant() != null) {
                                projectGrants.add(TransitionRemovalImpactDto.ProjectGrantInfo.builder()
                                        .projectId(project.getId())
                                        .projectName(project.getName())
                                        .build());
                            }
                        }
                    }
                    
                    boolean hasAssignments = !assignedRoles.isEmpty() || grantId != null || !projectGrants.isEmpty();
                    boolean canBePreserved = hasAssignments;
                    
                    WorkflowStatus fromStatus = transition.getFromStatus();
                    WorkflowStatus toStatus = transition.getToStatus();
                    
                    impacts.add(TransitionRemovalImpactDto.PermissionImpact.builder()
                            .permissionId(perm.getId())
                            .permissionType("EXECUTOR")
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .transitionId(transition.getId())
                            .transitionName(transition.getName())
                            .fromStatusName(fromStatus != null && fromStatus.getStatus() != null ? fromStatus.getStatus().getName() : null)
                            .toStatusName(toStatus != null && toStatus.getStatus() != null ? toStatus.getStatus().getName() : null)
                            // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                            .grantId(grantId)
                            .grantName(grantName)
                            .assignedRoles(assignedRoles)
                            .hasAssignments(hasAssignments)
                            .transitionIdMatch(transition.getId()) // ID della transition rimossa
                            .transitionNameMatch(transition.getName() != null ? transition.getName() : "")
                            .canBePreserved(canBePreserved)
                            .defaultPreserve(canBePreserved)
                            .projectGrants(projectGrants)
                            .build());
                }
            }
        }
        
        return impacts;
    }

    /**
     * Helper methods
     */
    private List<ItemTypeSet> findItemTypeSetsUsingWorkflow(Long workflowId, Tenant tenant) {
        return itemTypeSetRepository.findByItemTypeConfigurationsWorkflowIdAndTenant(workflowId, tenant);
    }

    private List<String> getStatusNames(Set<Long> statusIds, Tenant tenant) {
        return statusIds.stream()
                .map(id -> workflowStatusRepository.findByIdAndTenant(id, tenant)
                        .map(ws -> ws.getStatus().getName())
                        .orElse("Unknown"))
                .collect(Collectors.toList());
    }

    private List<String> getTransitionNames(Set<Long> transitionIds, Tenant tenant) {
        return transitionIds.stream()
                .map(id -> transitionRepository.findByIdAndTenant(id, tenant)
                        .map(Transition::getName)
                        .orElse("Unknown"))
                .collect(Collectors.toList());
    }

    private List<TransitionRemovalImpactDto.ItemTypeSetImpact> mapItemTypeSetImpacts(List<ItemTypeSet> itemTypeSets) {
        return itemTypeSets.stream()
                .map(its -> TransitionRemovalImpactDto.ItemTypeSetImpact.builder()
                        .itemTypeSetId(its.getId())
                        .itemTypeSetName(its.getName())
                        .projectId(its.getProject() != null ? its.getProject().getId() : null)
                        .projectName(its.getProject() != null ? its.getProject().getName() : null)
                        .build())
                .collect(Collectors.toList());
    }

    private List<StatusRemovalImpactDto.ItemTypeSetImpact> mapItemTypeSetImpactsForStatus(List<ItemTypeSet> itemTypeSets) {
        return itemTypeSets.stream()
                .map(its -> StatusRemovalImpactDto.ItemTypeSetImpact.builder()
                        .itemTypeSetId(its.getId())
                        .itemTypeSetName(its.getName())
                        .projectId(its.getProject() != null ? its.getProject().getId() : null)
                        .projectName(its.getProject() != null ? its.getProject().getName() : null)
                        .build())
                .collect(Collectors.toList());
    }
}


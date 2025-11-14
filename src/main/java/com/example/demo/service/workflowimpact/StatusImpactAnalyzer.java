package com.example.demo.service.workflowimpact;

import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.*;
import com.example.demo.service.PermissionAssignmentService;
import com.example.demo.service.ProjectPermissionAssignmentService;
import com.example.demo.service.workflow.WorkflowHelper;
import com.example.demo.service.workflowimpact.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatusImpactAnalyzer {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final TransitionRepository transitionRepository;
    private final ItemTypeSetRepository itemTypeSetRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;
    private final WorkflowHelper workflowHelper;

    public StatusImpactAnalysisResult analyzeStatusRemovalImpact(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedStatusIds
    ) {
        // Metodo legacy: calcola le transizioni rimosse in base agli stati rimossi
        // (potrebbe includere transizioni solo spostate)
        Workflow workflow = workflowRepository.findByIdAndTenant(workflowId, tenant)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        List<Transition> workflowTransitions = transitionRepository.findByWorkflowAndTenant(workflow, tenant);
        Map<Long, Set<Long>> transitionIdsByStatus = indexTransitionIdsByStatus(workflowTransitions);

        Set<Long> removedTransitionIds = removedStatusIds.stream()
                .flatMap(statusId -> transitionIdsByStatus.getOrDefault(statusId, Collections.emptySet()).stream())
                .collect(Collectors.toSet());

        return analyzeStatusRemovalImpact(tenant, workflowId, removedStatusIds, removedTransitionIds);
    }

    public StatusImpactAnalysisResult analyzeStatusRemovalImpact(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedStatusIds,
            Set<Long> actuallyRemovedTransitionIds
    ) {
        Workflow workflow = workflowRepository.findByIdAndTenant(workflowId, tenant)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        List<ItemTypeSet> allItemTypeSetsUsingWorkflow = findItemTypeSetsUsingWorkflow(workflowId, tenant);

        Map<Long, WorkflowStatus> workflowStatusById = workflow.getStatuses().stream()
                .filter(ws -> ws.getId() != null)
                .collect(Collectors.toMap(
                        WorkflowStatus::getId,
                        Function.identity(),
                        (existing, duplicate) -> existing));

        List<WorkflowStatus> removedWorkflowStatuses = removedStatusIds.stream()
                .map(workflowStatusById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Usa le transizioni effettivamente rimosse passate come parametro
        // invece di calcolarle in base agli stati rimossi
        Set<Long> removedTransitionIds = actuallyRemovedTransitionIds != null 
                ? actuallyRemovedTransitionIds 
                : Collections.emptySet();

        Set<Long> removedStatusEntityIds = removedWorkflowStatuses.stream()
                .map(WorkflowStatus::getStatus)
                .filter(Objects::nonNull)
                .map(Status::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<StatusOwnerPermissionImpactData> statusOwnerPermissions =
                analyzeStatusOwnerPermissionImpacts(allItemTypeSetsUsingWorkflow, removedStatusIds);

        List<ExecutorPermissionImpactData> executorPermissionImpacts =
                removedTransitionIds.isEmpty()
                        ? Collections.emptyList()
                        : analyzeExecutorPermissionImpacts(allItemTypeSetsUsingWorkflow, removedTransitionIds);

        List<FieldStatusPermissionImpactData> fieldStatusPermissions =
                analyzeFieldStatusPermissionImpacts(allItemTypeSetsUsingWorkflow, removedStatusIds, removedStatusEntityIds);

        Set<Long> itemTypeSetIdsWithImpact = new HashSet<>();
        statusOwnerPermissions.stream()
                .map(StatusOwnerPermissionImpactData::getItemTypeSetId)
                .filter(Objects::nonNull)
                .forEach(itemTypeSetIdsWithImpact::add);
        executorPermissionImpacts.stream()
                .map(ExecutorPermissionImpactData::getItemTypeSetId)
                .filter(Objects::nonNull)
                .forEach(itemTypeSetIdsWithImpact::add);
        fieldStatusPermissions.stream()
                .map(FieldStatusPermissionImpactData::getItemTypeSetId)
                .filter(Objects::nonNull)
                .forEach(itemTypeSetIdsWithImpact::add);

        List<ItemTypeSet> affectedItemTypeSets = allItemTypeSetsUsingWorkflow.stream()
                .filter(its -> itemTypeSetIdsWithImpact.contains(its.getId()))
                .collect(Collectors.toList());

        List<String> removedStatusNames = getStatusNames(removedStatusIds, tenant);
        List<String> removedTransitionNames = workflowHelper.getTransitionNames(removedTransitionIds, tenant);

        return StatusImpactAnalysisResult.builder()
                .workflow(workflow)
                .removedStatusIds(removedStatusIds)
                .removedStatusNames(removedStatusNames)
                .removedTransitionIds(removedTransitionIds)
                .removedTransitionNames(removedTransitionNames)
                .affectedItemTypeSets(affectedItemTypeSets)
                .statusOwnerPermissionImpacts(statusOwnerPermissions)
                .executorPermissionImpacts(executorPermissionImpacts)
                .fieldStatusPermissionImpacts(fieldStatusPermissions)
                .build();
    }

    private List<StatusOwnerPermissionImpactData> analyzeStatusOwnerPermissionImpacts(
            List<ItemTypeSet> itemTypeSets,
            Set<Long> removedStatusIds
    ) {
        List<StatusOwnerPermissionImpactData> impacts = new ArrayList<>();

        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                List<StatusOwnerPermission> permissions = statusOwnerPermissionRepository
                        .findByItemTypeConfigurationAndWorkflowStatusIdIn(config, removedStatusIds);

                for (StatusOwnerPermission perm : permissions) {
                    WorkflowStatus workflowStatus = perm.getWorkflowStatus();
                    if (workflowStatus == null || !removedStatusIds.contains(workflowStatus.getId())) {
                        continue;
                    }

                    AssignmentData assignmentData = loadAssignmentData(
                            "StatusOwnerPermission",
                            perm.getId(),
                            itemTypeSet,
                            itemTypeSet.getTenant());

                    // Quando si rimuove uno stato, le permission associate non possono essere preservate
                    // perché lo stato stesso viene rimosso
                    impacts.add(StatusOwnerPermissionImpactData.builder()
                            .permissionId(perm.getId())
                            .permissionType("STATUS_OWNER")
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .workflowStatusId(workflowStatus.getId())
                            .workflowStatusName(workflowStatus.getStatus() != null ? workflowStatus.getStatus().getName() : null)
                            .statusName(workflowStatus.getStatus() != null ? workflowStatus.getStatus().getName() : null)
                            .statusCategory(workflowStatus.getStatusCategory() != null ? workflowStatus.getStatusCategory().name() : null)
                            .grantId(assignmentData.grantId())
                            .grantName(assignmentData.grantName())
                            .assignedRoles(assignmentData.assignedRoles())
                            .hasAssignments(assignmentData.hasAssignments())
                            .canBePreserved(false) // Non può essere preservata quando lo stato viene rimosso
                            .defaultPreserve(false)
                            .projectGrants(assignmentData.projectGrants())
                            .build());
                }
            }
        }

        return impacts;
    }

    private List<FieldStatusPermissionImpactData> analyzeFieldStatusPermissionImpacts(
            List<ItemTypeSet> itemTypeSets,
            Set<Long> removedWorkflowStatusIds,
            Set<Long> removedStatusEntityIds
    ) {
        List<FieldStatusPermissionImpactData> impacts = new ArrayList<>();

        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                List<FieldStatusPermission> permissions = fieldStatusPermissionRepository
                        .findAllByItemTypeConfiguration(config);

                for (FieldStatusPermission perm : permissions) {
                    WorkflowStatus workflowStatus = perm.getWorkflowStatus();
                    if (workflowStatus == null || workflowStatus.getStatus() == null) {
                        continue;
                    }

                    Long statusEntityId = workflowStatus.getStatus().getId();
                    if (!removedStatusEntityIds.contains(statusEntityId)) {
                        continue;
                    }

                    if (!removedWorkflowStatusIds.contains(workflowStatus.getId())) {
                        continue;
                    }

                    Field field = perm.getField();
                    FieldConfiguration fieldConfig = resolveFieldConfiguration(config, field);

                    if (fieldConfig == null) {
                        continue;
                    }

                    AssignmentData assignmentData = loadAssignmentData(
                            "FieldStatusPermission",
                            perm.getId(),
                            itemTypeSet,
                            itemTypeSet.getTenant());

                    // Quando si rimuove uno stato, le permission associate non possono essere preservate
                    // perché lo stato stesso viene rimosso
                    boolean canBePreserved = false;

                    impacts.add(FieldStatusPermissionImpactData.builder()
                            .permissionId(perm.getId())
                            .permissionType(perm.getPermissionType() == FieldStatusPermission.PermissionType.EDITORS
                                    ? "FIELD_EDITORS"
                                    : "FIELD_VIEWERS")
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .fieldId(field != null ? field.getId() : null)
                            .fieldName(field != null ? field.getName() : null)
                            .workflowStatusId(workflowStatus.getId())
                            .workflowStatusName(workflowStatus.getStatus() != null ? workflowStatus.getStatus().getName() : null)
                            .statusName(workflowStatus.getStatus() != null ? workflowStatus.getStatus().getName() : null)
                            .grantId(assignmentData.grantId())
                            .grantName(assignmentData.grantName())
                            .assignedRoles(assignmentData.assignedRoles())
                            .hasAssignments(assignmentData.hasAssignments())
                            .canBePreserved(canBePreserved)
                            .defaultPreserve(canBePreserved)
                            .projectGrants(assignmentData.projectGrants())
                            .build());
                }
            }
        }

        return impacts;
    }

    private List<ExecutorPermissionImpactData> analyzeExecutorPermissionImpacts(
            List<ItemTypeSet> itemTypeSets,
            Set<Long> removedTransitionIds
    ) {
        List<ExecutorPermissionImpactData> impacts = new ArrayList<>();

        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                List<ExecutorPermission> permissions = executorPermissionRepository
                        .findByItemTypeConfigurationAndTransitionIdIn(config, removedTransitionIds);

                for (ExecutorPermission perm : permissions) {
                    Transition transition = perm.getTransition();
                    if (transition == null || !removedTransitionIds.contains(transition.getId())) {
                        continue;
                    }

                    AssignmentData assignmentData = loadAssignmentData(
                            "ExecutorPermission",
                            perm.getId(),
                            itemTypeSet,
                            itemTypeSet.getTenant());

                    WorkflowStatus fromStatus = transition.getFromStatus();
                    WorkflowStatus toStatus = transition.getToStatus();

                    // Quando si rimuove uno stato, le transizioni associate vengono rimosse,
                    // quindi le ExecutorPermission non possono essere preservate
                    impacts.add(ExecutorPermissionImpactData.builder()
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
                            .grantId(assignmentData.grantId())
                            .grantName(assignmentData.grantName())
                            .assignedRoles(assignmentData.assignedRoles())
                            .hasAssignments(assignmentData.hasAssignments())
                            .transitionIdMatch(transition.getId())
                            .transitionNameMatch(transition.getName() != null ? transition.getName() : "")
                            .canBePreserved(false) // Non può essere preservata quando la transizione viene rimossa insieme allo stato
                            .defaultPreserve(false)
                            .projectGrants(assignmentData.projectGrants())
                            .build());
                }
            }
        }

        return impacts;
    }

    private Map<Long, Set<Long>> indexTransitionIdsByStatus(List<Transition> transitions) {
        Map<Long, Set<Long>> transitionIdsByStatus = new HashMap<>();
        for (Transition transition : transitions) {
            Long transitionId = transition.getId();
            if (transitionId == null) {
                continue;
            }

            WorkflowStatus fromStatus = transition.getFromStatus();
            if (fromStatus != null && fromStatus.getId() != null) {
                transitionIdsByStatus
                        .computeIfAbsent(fromStatus.getId(), ignore -> new HashSet<>())
                        .add(transitionId);
            }

            WorkflowStatus toStatus = transition.getToStatus();
            if (toStatus != null && toStatus.getId() != null) {
                transitionIdsByStatus
                        .computeIfAbsent(toStatus.getId(), ignore -> new HashSet<>())
                        .add(transitionId);
            }
        }
        return transitionIdsByStatus;
    }

    private FieldConfiguration resolveFieldConfiguration(ItemTypeConfiguration config, Field field) {
        if (config.getFieldSet() == null || config.getFieldSet().getFieldSetEntries() == null) {
            return null;
        }

        return config.getFieldSet().getFieldSetEntries().stream()
                .map(FieldSetEntry::getFieldConfiguration)
                .filter(Objects::nonNull)
                .filter(fc -> fc.getField() != null && fc.getField().getId() != null)
                .filter(fc -> field != null && fc.getField().getId().equals(field.getId()))
                .findFirst()
                .orElse(null);
    }

    private AssignmentData loadAssignmentData(
            String permissionType,
            Long permissionId,
            ItemTypeSet itemTypeSet,
            Tenant tenant
    ) {
        List<String> assignedRoles = new ArrayList<>();
        Long grantId = null;
        String grantName = null;
        List<ProjectGrantData> projectGrants = new ArrayList<>();

        Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                permissionType,
                permissionId,
                tenant);

        if (assignmentOpt.isPresent()) {
            PermissionAssignment assignment = assignmentOpt.get();
            assignment.getRoles().forEach(role -> assignedRoles.add(role.getName()));
            if (assignment.getGrant() != null) {
                Grant grant = assignment.getGrant();
                grantId = grant.getId();
                grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
            }
        }

        if (itemTypeSet.getProject() != null) {
            Optional<PermissionAssignment> projectAssignmentOpt =
                    projectPermissionAssignmentService.getProjectAssignment(
                            permissionType,
                            permissionId,
                            itemTypeSet.getProject().getId(),
                            tenant);
            if (projectAssignmentOpt.isPresent() && projectAssignmentOpt.get().getGrant() != null) {
                projectGrants.add(ProjectGrantData.builder()
                        .projectId(itemTypeSet.getProject().getId())
                        .projectName(itemTypeSet.getProject().getName())
                        .build());
            }
        } else {
            for (Project project : itemTypeSet.getProjectsAssociation()) {
                Optional<PermissionAssignment> projectAssignmentOpt =
                        projectPermissionAssignmentService.getProjectAssignment(
                                permissionType,
                                permissionId,
                                project.getId(),
                                tenant);
                if (projectAssignmentOpt.isPresent() && projectAssignmentOpt.get().getGrant() != null) {
                    projectGrants.add(ProjectGrantData.builder()
                            .projectId(project.getId())
                            .projectName(project.getName())
                            .build());
                }
            }
        }

        boolean hasAssignments = !assignedRoles.isEmpty() || grantId != null || !projectGrants.isEmpty();

        return new AssignmentData(
                List.copyOf(assignedRoles),
                grantId,
                grantName,
                List.copyOf(projectGrants),
                hasAssignments);
    }

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


    private record AssignmentData(
            List<String> assignedRoles,
            Long grantId,
            String grantName,
            List<ProjectGrantData> projectGrants,
            boolean hasAssignments
    ) {
    }
}


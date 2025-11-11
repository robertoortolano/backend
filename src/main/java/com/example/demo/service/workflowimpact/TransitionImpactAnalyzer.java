package com.example.demo.service.workflowimpact;

import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ExecutorPermissionRepository;
import com.example.demo.repository.ItemTypeSetRepository;
import com.example.demo.repository.TransitionRepository;
import com.example.demo.repository.WorkflowRepository;
import com.example.demo.service.PermissionAssignmentService;
import com.example.demo.service.ProjectPermissionAssignmentService;
import com.example.demo.service.workflowimpact.model.ExecutorPermissionImpactData;
import com.example.demo.service.workflowimpact.model.ProjectGrantData;
import com.example.demo.service.workflowimpact.model.TransitionImpactAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransitionImpactAnalyzer {

    private final WorkflowRepository workflowRepository;
    private final TransitionRepository transitionRepository;
    private final ItemTypeSetRepository itemTypeSetRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;

    public TransitionImpactAnalysisResult analyzeTransitionRemovalImpact(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedTransitionIds
    ) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        if (!workflow.getTenant().equals(tenant)) {
            throw new ApiException("Workflow does not belong to tenant");
        }

        List<ItemTypeSet> allItemTypeSetsUsingWorkflow = findItemTypeSetsUsingWorkflow(workflowId, tenant);

        List<ExecutorPermissionImpactData> executorPermissionImpacts =
                analyzeExecutorPermissionImpacts(allItemTypeSetsUsingWorkflow, removedTransitionIds);

        Set<Long> itemTypeSetIdsWithImpact = executorPermissionImpacts.stream()
                .map(ExecutorPermissionImpactData::getItemTypeSetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<ItemTypeSet> affectedItemTypeSets = allItemTypeSetsUsingWorkflow.stream()
                .filter(its -> itemTypeSetIdsWithImpact.contains(its.getId()))
                .collect(Collectors.toList());

        List<String> removedTransitionNames = getTransitionNames(removedTransitionIds, tenant);

        return TransitionImpactAnalysisResult.builder()
                .workflow(workflow)
                .removedTransitionIds(removedTransitionIds)
                .removedTransitionNames(removedTransitionNames)
                .affectedItemTypeSets(affectedItemTypeSets)
                .executorPermissionImpacts(executorPermissionImpacts)
                .build();
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
                            .canBePreserved(assignmentData.hasAssignments())
                            .defaultPreserve(assignmentData.hasAssignments())
                            .projectGrants(assignmentData.projectGrants())
                            .build());
                }
            }
        }

        return impacts;
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

    private List<String> getTransitionNames(Set<Long> transitionIds, Tenant tenant) {
        return transitionIds.stream()
                .map(id -> transitionRepository.findByIdAndTenant(id, tenant)
                        .map(Transition::getName)
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


package com.example.demo.service.migration.analysis;

import com.example.demo.dto.ItemTypeConfigurationMigrationImpactDto;
import com.example.demo.entity.ExecutorPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.PermissionAssignment;
import com.example.demo.entity.Project;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Transition;
import com.example.demo.repository.ExecutorPermissionRepository;
import com.example.demo.service.PermissionAssignmentService;
import com.example.demo.service.ProjectPermissionAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ExecutorPermissionAnalysisStrategy {

    private final ExecutorPermissionRepository executorPermissionRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;

    public List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyze(MigrationAnalysisContext context) {
        if (!context.workflowChanged()) {
            return Collections.emptyList();
        }

        ItemTypeConfiguration configuration = context.configuration();
        List<ExecutorPermission> existingPermissions = executorPermissionRepository.findAllByItemTypeConfiguration(configuration);
        if (existingPermissions.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> newTransitionIds = context.newWorkflowInfo().getTransitions().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.TransitionInfo::getTransitionId)
                .collect(Collectors.toSet());
        Map<Long, ItemTypeConfigurationMigrationImpactDto.TransitionInfo> newTransitionsMap = context.newWorkflowInfo().getTransitions().stream()
                .collect(Collectors.toMap(
                        ItemTypeConfigurationMigrationImpactDto.TransitionInfo::getTransitionId,
                        Function.identity()
                ));

        Tenant tenant = configuration.getTenant();
        Long itemTypeSetId = context.itemTypeSetId();
        ItemTypeSet itemTypeSet = context.owningItemTypeSet();

        return existingPermissions.stream()
                .map(permission -> buildImpact(permission, context, tenant, itemTypeSet, itemTypeSetId, newTransitionIds, newTransitionsMap))
                .collect(Collectors.toList());
    }

    private ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact buildImpact(
            ExecutorPermission permission,
            MigrationAnalysisContext context,
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            Long itemTypeSetId,
            Set<Long> newTransitionIds,
            Map<Long, ItemTypeConfigurationMigrationImpactDto.TransitionInfo> newTransitionsMap
    ) {
        Transition transition = permission.getTransition();
        Long transitionId = transition != null ? transition.getId() : null;

        boolean canPreserve = transitionId != null && newTransitionIds.contains(transitionId);
        ItemTypeConfigurationMigrationImpactDto.TransitionInfo matchingTransition = canPreserve
                ? newTransitionsMap.get(transitionId)
                : null;

        Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment("ExecutorPermission", permission.getId(), tenant);
        List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toList()))
                .orElseGet(ArrayList::new);

        Long grantId = null;
        String grantName = null;
        if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
            grantId = assignmentOpt.get().getGrant().getId();
            grantName = assignmentOpt.get().getGrant().getRole() != null
                    ? assignmentOpt.get().getGrant().getRole().getName()
                    : "Grant globale";
        }

        List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrants = collectProjectGrants(
                permission,
                tenant,
                itemTypeSet
        );

        boolean hasAssignments = !assignedRoles.isEmpty() || grantId != null || !projectGrants.isEmpty();
        boolean defaultPreserve = canPreserve && hasAssignments;

        String transitionName = transition != null ? transition.getName() : null;
        String fromStatusName = transition != null && transition.getFromStatus() != null && transition.getFromStatus().getStatus() != null
                ? transition.getFromStatus().getStatus().getName()
                : null;
        String toStatusName = transition != null && transition.getToStatus() != null && transition.getToStatus().getStatus() != null
                ? transition.getToStatus().getStatus().getName()
                : null;

        return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                .permissionId(permission.getId())
                .permissionType("EXECUTORS")
                .entityId(transitionId)
                .entityName(transitionName != null ? transitionName : formatTransitionName(fromStatusName, toStatusName))
                .matchingEntityId(canPreserve && matchingTransition != null ? matchingTransition.getTransitionId() : null)
                .matchingEntityName(canPreserve && matchingTransition != null ? matchingTransition.getTransitionName() : null)
                .assignedRoles(assignedRoles)
                .hasAssignments(hasAssignments)
                .canBePreserved(canPreserve)
                .defaultPreserve(defaultPreserve)
                .suggestedAction(canPreserve ? "PRESERVE" : "REMOVE")
                .itemTypeSetId(itemTypeSetId)
                .itemTypeSetName(context.itemTypeSetName())
                .projectId(itemTypeSet != null && itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                .projectName(itemTypeSet != null && itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                .grantId(grantId)
                .grantName(grantName)
                .projectGrants(projectGrants)
                .fromStatusName(fromStatusName)
                .toStatusName(toStatusName)
                .transitionName(transitionName)
                .build();
    }

    private String formatTransitionName(String fromStatusName, String toStatusName) {
        if (fromStatusName == null && toStatusName == null) {
            return null;
        }
        return String.format("%s -> %s",
                fromStatusName != null ? fromStatusName : "?",
                toStatusName != null ? toStatusName : "?"
        );
    }

    private List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> collectProjectGrants(
            ExecutorPermission permission,
            Tenant tenant,
            ItemTypeSet itemTypeSet
    ) {
        if (itemTypeSet == null) {
            return Collections.emptyList();
        }

        List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> grants = new ArrayList<>();

        if (itemTypeSet.getProject() != null) {
            Optional<PermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                    "ExecutorPermission",
                    permission.getId(),
                    itemTypeSet.getProject().getId(),
                    tenant
            );
            projectAssignmentOpt.ifPresent(assignment -> addGrantIfPresent(grants, assignment, itemTypeSet.getProject().getId(), itemTypeSet.getProject().getName()));
        } else if (itemTypeSet.getProjectsAssociation() != null) {
            for (Project project : itemTypeSet.getProjectsAssociation()) {
                Optional<PermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                        "ExecutorPermission",
                        permission.getId(),
                        project.getId(),
                        tenant
                );
                projectAssignmentOpt.ifPresent(assignment -> addGrantIfPresent(grants, assignment, project.getId(), project.getName()));
            }
        }

        return grants;
    }

    private void addGrantIfPresent(
            List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> grants,
            PermissionAssignment assignment,
            Long projectId,
            String projectName
    ) {
        if (assignment.getGrant() != null) {
            grants.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                    .projectId(projectId)
                    .projectName(projectName)
                    .build());
        }
    }
}


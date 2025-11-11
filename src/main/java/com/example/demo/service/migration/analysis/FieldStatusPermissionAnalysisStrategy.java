package com.example.demo.service.migration.analysis;

import com.example.demo.dto.ItemTypeConfigurationMigrationImpactDto;
import com.example.demo.entity.FieldStatusPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.PermissionAssignment;
import com.example.demo.entity.Project;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.repository.FieldStatusPermissionRepository;
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
public class FieldStatusPermissionAnalysisStrategy {

    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;

    public List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyze(MigrationAnalysisContext context) {
        if (!context.fieldSetChanged() && !context.workflowChanged()) {
            return Collections.emptyList();
        }

        ItemTypeConfiguration configuration = context.configuration();
        List<FieldStatusPermission> existingPermissions = fieldStatusPermissionRepository.findAllByItemTypeConfiguration(configuration);
        if (existingPermissions.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> newFieldIds = context.newFieldSetInfo().getFields().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.FieldInfo::getFieldId)
                .collect(Collectors.toSet());

        Set<Long> newStatusIds = context.newWorkflowInfo().getWorkflowStatuses().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo::getStatusId)
                .collect(Collectors.toSet());

        Map<Long, ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo> newStatusesMap = context.newWorkflowInfo().getWorkflowStatuses().stream()
                .collect(Collectors.toMap(
                        ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo::getStatusId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        Tenant tenant = configuration.getTenant();
        Long itemTypeSetId = context.itemTypeSetId();
        ItemTypeSet itemTypeSet = context.owningItemTypeSet();

        return existingPermissions.stream()
                .map(permission -> buildImpact(permission, context, tenant, itemTypeSet, itemTypeSetId, newFieldIds, newStatusIds, newStatusesMap))
                .collect(Collectors.toList());
    }

    private ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact buildImpact(
            FieldStatusPermission permission,
            MigrationAnalysisContext context,
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            Long itemTypeSetId,
            Set<Long> newFieldIds,
            Set<Long> newStatusIds,
            Map<Long, ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo> newStatusesMap
    ) {
        Long fieldId = permission.getField().getId();
        Long statusId = permission.getWorkflowStatus().getStatus().getId();

        boolean fieldExists = newFieldIds.contains(fieldId);
        boolean statusExists = newStatusIds.contains(statusId);
        boolean canPreserve = fieldExists && statusExists;
        ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo matchingStatus = statusExists ? newStatusesMap.get(statusId) : null;

        Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment("FieldStatusPermission", permission.getId(), tenant);
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
        String suggestedAction = canPreserve ? "PRESERVE" : "REMOVE";

        String permissionType = permission.getPermissionType() == FieldStatusPermission.PermissionType.EDITORS
                ? "FIELD_EDITORS"
                : "FIELD_VIEWERS";

        return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                .permissionId(permission.getId())
                .permissionType(permissionType)
                .entityId(statusId)
                .entityName(permission.getWorkflowStatus().getStatus().getName())
                .fieldId(fieldId)
                .fieldName(permission.getField().getName())
                .matchingEntityId(canPreserve && matchingStatus != null ? matchingStatus.getStatusId() : null)
                .matchingEntityName(canPreserve && matchingStatus != null ? matchingStatus.getStatusName() : null)
                .assignedRoles(assignedRoles)
                .hasAssignments(hasAssignments)
                .canBePreserved(canPreserve)
                .defaultPreserve(defaultPreserve)
                .suggestedAction(suggestedAction)
                .itemTypeSetId(itemTypeSetId)
                .itemTypeSetName(context.itemTypeSetName())
                .projectId(itemTypeSet != null && itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                .projectName(itemTypeSet != null && itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                .grantId(grantId)
                .grantName(grantName)
                .projectGrants(projectGrants)
                .build();
    }

    private List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> collectProjectGrants(
            FieldStatusPermission permission,
            Tenant tenant,
            ItemTypeSet itemTypeSet
    ) {
        if (itemTypeSet == null) {
            return Collections.emptyList();
        }

        List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> grants = new ArrayList<>();

        if (itemTypeSet.getProject() != null) {
            Optional<PermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                    "FieldStatusPermission",
                    permission.getId(),
                    itemTypeSet.getProject().getId(),
                    tenant
            );
            projectAssignmentOpt.ifPresent(assignment -> {
                if (assignment.getGrant() != null) {
                    grants.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                            .projectId(itemTypeSet.getProject().getId())
                            .projectName(itemTypeSet.getProject().getName())
                            .build());
                }
            });
        } else if (itemTypeSet.getProjectsAssociation() != null) {
            for (Project project : itemTypeSet.getProjectsAssociation()) {
                Optional<PermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                        "FieldStatusPermission",
                        permission.getId(),
                        project.getId(),
                        tenant
                );
                projectAssignmentOpt.ifPresent(assignment -> {
                    if (assignment.getGrant() != null) {
                        grants.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                .projectId(project.getId())
                                .projectName(project.getName())
                                .build());
                    }
                });
            }
        }

        return grants;
    }
}


package com.example.demo.service.migration.analysis;

import com.example.demo.dto.ItemTypeConfigurationMigrationImpactDto;
import com.example.demo.entity.FieldConfiguration;
import com.example.demo.entity.FieldOwnerPermission;
import com.example.demo.entity.FieldSet;
import com.example.demo.entity.FieldSetEntry;
import com.example.demo.entity.Grant;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.PermissionAssignment;
import com.example.demo.entity.Project;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.repository.FieldOwnerPermissionRepository;
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
public class FieldOwnerPermissionAnalysisStrategy {

    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;

    public List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyze(MigrationAnalysisContext context) {
        if (!context.fieldSetChanged()) {
            return Collections.emptyList();
        }

        ItemTypeConfiguration configuration = context.configuration();
        ItemTypeConfigurationMigrationImpactDto.FieldSetInfo newFieldSetInfo = context.newFieldSetInfo();

        List<FieldOwnerPermission> existingPermissions = fieldOwnerPermissionRepository.findAllByItemTypeConfiguration(configuration);
        if (existingPermissions.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> newFieldIds = newFieldSetInfo.getFields().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.FieldInfo::getFieldId)
                .collect(Collectors.toSet());
        Map<Long, ItemTypeConfigurationMigrationImpactDto.FieldInfo> newFieldsMap = newFieldSetInfo.getFields().stream()
                .collect(Collectors.toMap(
                        ItemTypeConfigurationMigrationImpactDto.FieldInfo::getFieldId,
                        Function.identity()
                ));

        Tenant tenant = configuration.getTenant();
        Long itemTypeSetId = context.itemTypeSetId();
        ItemTypeSet itemTypeSet = context.owningItemTypeSet();

        return existingPermissions.stream()
                .map(permission -> buildImpact(permission, context, tenant, itemTypeSet, itemTypeSetId, newFieldIds, newFieldsMap))
                .collect(Collectors.toList());
    }

    private ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact buildImpact(
            FieldOwnerPermission permission,
            MigrationAnalysisContext context,
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            Long itemTypeSetId,
            Set<Long> newFieldIds,
            Map<Long, ItemTypeConfigurationMigrationImpactDto.FieldInfo> newFieldsMap
    ) {
        ItemTypeConfiguration configuration = context.configuration();
        Long fieldId = permission.getField().getId();
        boolean canPreserve = newFieldIds.contains(fieldId);

        ItemTypeConfigurationMigrationImpactDto.FieldInfo matchingField = canPreserve
                ? newFieldsMap.get(fieldId)
                : null;

        Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment("FieldOwnerPermission", permission.getId(), tenant);
        List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toList()))
                .orElseGet(ArrayList::new);

        Long grantId = null;
        String grantName = null;
        if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
            Grant grant = assignmentOpt.get().getGrant();
            grantId = grant.getId();
            grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
        }

        List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrants = collectProjectGrants(
                permission,
                tenant,
                itemTypeSet,
                context.oldFieldSet()
        );

        boolean hasAssignments = !assignedRoles.isEmpty() || grantId != null || !projectGrants.isEmpty();
        boolean defaultPreserve = canPreserve && hasAssignments;

        return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                .permissionId(permission.getId())
                .permissionType("FIELD_OWNERS")
                .entityId(fieldId)
                .entityName(permission.getField().getName())
                .matchingEntityId(canPreserve && matchingField != null ? matchingField.getFieldId() : null)
                .matchingEntityName(canPreserve && matchingField != null ? matchingField.getFieldName() : null)
                .assignedRoles(assignedRoles)
                .hasAssignments(hasAssignments)
                .canBePreserved(canPreserve)
                .defaultPreserve(defaultPreserve)
                .suggestedAction(canPreserve ? "PRESERVE" : "REMOVE")
                .itemTypeSetId(itemTypeSetId)
                .itemTypeSetName(context.itemTypeSetName())
                .projectId(configuration.getProject() != null ? configuration.getProject().getId() : null)
                .projectName(configuration.getProject() != null ? configuration.getProject().getName() : null)
                .grantId(grantId)
                .grantName(grantName)
                .projectGrants(projectGrants)
                .build();
    }

    private List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> collectProjectGrants(
            FieldOwnerPermission permission,
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            FieldSet oldFieldSet
    ) {
        if (itemTypeSet == null) {
            return Collections.emptyList();
        }

        List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> grants = new ArrayList<>();

        if (!containsField(oldFieldSet, permission)) {
            return grants;
        }

        if (itemTypeSet.getProject() != null) {
            Optional<PermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                    "FieldOwnerPermission",
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
                        "FieldOwnerPermission",
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

    private boolean containsField(FieldSet oldFieldSet, FieldOwnerPermission permission) {
        if (oldFieldSet == null) {
            return false;
        }
        return oldFieldSet.getFieldSetEntries()
                .stream()
                .map(FieldSetEntry::getFieldConfiguration)
                .map(FieldConfiguration::getField)
                .anyMatch(field -> field.getId().equals(permission.getField().getId()));
    }
}


package com.example.demo.service.itemtypeset;

import com.example.demo.entity.ExecutorPermission;
import com.example.demo.entity.FieldOwnerPermission;
import com.example.demo.entity.FieldStatusPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Project;
import com.example.demo.entity.StatusOwnerPermission;
import com.example.demo.entity.Tenant;
import com.example.demo.repository.ExecutorPermissionRepository;
import com.example.demo.repository.FieldOwnerPermissionRepository;
import com.example.demo.repository.FieldStatusPermissionRepository;
import com.example.demo.repository.ItemTypeSetRepository;
import com.example.demo.repository.StatusOwnerPermissionRepository;
import com.example.demo.service.PermissionAssignmentService;
import com.example.demo.service.ProjectPermissionAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class ItemTypeSetPermissionCleanupModule {

    private final ItemTypeSetRepository itemTypeSetRepository;
    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;

    public void removeOrphanedPermissionsForItemTypeConfigurations(
            Tenant tenant,
            Long itemTypeSetId,
            Set<Long> removedItemTypeConfigurationIds,
            Set<Long> preservedPermissionIds
    ) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByIdAndTenant(itemTypeSetId, tenant)
                .orElseThrow(() -> new com.example.demo.exception.ApiException("ItemTypeSet not found: " + itemTypeSetId));

        List<ItemTypeConfiguration> configsToRemove = itemTypeSet.getItemTypeConfigurations().stream()
                .filter(config -> removedItemTypeConfigurationIds.contains(config.getId()))
                .collect(Collectors.toList());

        if (configsToRemove.isEmpty()) {
            return;
        }

        for (ItemTypeConfiguration config : configsToRemove) {
            deleteFieldOwnerPermissions(tenant, itemTypeSet, preservedPermissionIds, config);
            deleteStatusOwnerPermissions(tenant, itemTypeSet, preservedPermissionIds, config);
            deleteFieldStatusPermissions(tenant, itemTypeSet, preservedPermissionIds, config);
            deleteExecutorPermissions(tenant, itemTypeSet, preservedPermissionIds, config);
        }
    }

    private void deleteFieldOwnerPermissions(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            Set<Long> preservedPermissionIds,
            ItemTypeConfiguration config
    ) {
        List<FieldOwnerPermission> fieldOwnerPermissions = fieldOwnerPermissionRepository.findAllByItemTypeConfiguration(config);
        for (FieldOwnerPermission perm : fieldOwnerPermissions) {
            if (isPreserved(preservedPermissionIds, perm.getId())) {
                continue;
            }
            permissionAssignmentService.deleteAssignment("FieldOwnerPermission", perm.getId(), tenant);
            deleteProjectAssignments(itemTypeSet, perm.getId(), "FieldOwnerPermission", tenant);
            fieldOwnerPermissionRepository.delete(perm);
        }
    }

    private void deleteStatusOwnerPermissions(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            Set<Long> preservedPermissionIds,
            ItemTypeConfiguration config
    ) {
        List<StatusOwnerPermission> statusOwnerPermissions = statusOwnerPermissionRepository.findByItemTypeConfigurationIdAndTenant(config.getId(), itemTypeSet.getTenant());
        for (StatusOwnerPermission perm : statusOwnerPermissions) {
            if (isPreserved(preservedPermissionIds, perm.getId())) {
                continue;
            }
            permissionAssignmentService.deleteAssignment("StatusOwnerPermission", perm.getId(), tenant);
            deleteProjectAssignments(itemTypeSet, perm.getId(), "StatusOwnerPermission", tenant);
            statusOwnerPermissionRepository.delete(perm);
        }
    }

    private void deleteFieldStatusPermissions(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            Set<Long> preservedPermissionIds,
            ItemTypeConfiguration config
    ) {
        List<FieldStatusPermission> fieldStatusPermissions = fieldStatusPermissionRepository.findByItemTypeConfigurationIdAndTenant(config.getId(), itemTypeSet.getTenant());
        for (FieldStatusPermission perm : fieldStatusPermissions) {
            if (isPreserved(preservedPermissionIds, perm.getId())) {
                continue;
            }
            permissionAssignmentService.deleteAssignment("FieldStatusPermission", perm.getId(), tenant);
            deleteProjectAssignments(itemTypeSet, perm.getId(), "FieldStatusPermission", tenant);
            fieldStatusPermissionRepository.delete(perm);
        }
    }

    private void deleteExecutorPermissions(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            Set<Long> preservedPermissionIds,
            ItemTypeConfiguration config
    ) {
        List<ExecutorPermission> executorPermissions = executorPermissionRepository.findAllByItemTypeConfiguration(config);
        for (ExecutorPermission perm : executorPermissions) {
            if (isPreserved(preservedPermissionIds, perm.getId())) {
                continue;
            }
            permissionAssignmentService.deleteAssignment("ExecutorPermission", perm.getId(), tenant);
            deleteProjectAssignments(itemTypeSet, perm.getId(), "ExecutorPermission", tenant);
            executorPermissionRepository.delete(perm);
        }
    }

    private void deleteProjectAssignments(
            ItemTypeSet itemTypeSet,
            Long permissionId,
            String permissionType,
            Tenant tenant
    ) {
        if (itemTypeSet.getProject() != null) {
            projectPermissionAssignmentService.deleteProjectAssignment(
                    permissionType,
                    permissionId,
                    itemTypeSet.getProject().getId(),
                    tenant
            );
            return;
        }

        if (itemTypeSet.getProjectsAssociation() == null) {
            return;
        }

        for (Project project : itemTypeSet.getProjectsAssociation()) {
            projectPermissionAssignmentService.deleteProjectAssignment(
                    permissionType,
                    permissionId,
                    project.getId(),
                    tenant
            );
        }
    }

    private boolean isPreserved(Set<Long> preservedPermissionIds, Long permissionId) {
        return preservedPermissionIds != null && preservedPermissionIds.contains(permissionId);
    }
}



package com.example.demo.service.itemtypeset;

import com.example.demo.dto.ItemTypeConfigurationRemovalImpactDto;
import com.example.demo.dto.ItemTypeConfigurationRemovalImpactDto.PermissionImpact;
import com.example.demo.entity.CreatorPermission;
import com.example.demo.entity.ExecutorPermission;
import com.example.demo.entity.FieldOwnerPermission;
import com.example.demo.entity.FieldStatusPermission;
import com.example.demo.entity.Grant;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.PermissionAssignment;
import com.example.demo.entity.Project;
import com.example.demo.entity.Role;
import com.example.demo.entity.StatusOwnerPermission;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Transition;
import com.example.demo.entity.WorkerPermission;
import com.example.demo.repository.CreatorPermissionRepository;
import com.example.demo.repository.ExecutorPermissionRepository;
import com.example.demo.repository.FieldOwnerPermissionRepository;
import com.example.demo.repository.FieldStatusPermissionRepository;
import com.example.demo.repository.ItemTypeConfigurationRepository;
import com.example.demo.repository.ItemTypeSetRepository;
import com.example.demo.repository.StatusOwnerPermissionRepository;
import com.example.demo.repository.WorkerPermissionRepository;
import com.example.demo.service.ItemTypePermissionService;
import com.example.demo.service.ItemTypeSetPermissionService;
import com.example.demo.service.PermissionAssignmentService;
import com.example.demo.service.ProjectPermissionAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ItemTypeSetPermissionOrchestrator {

    private final ItemTypeSetRepository itemTypeSetRepository;
    private final ItemTypeConfigurationRepository itemTypeConfigurationRepository;

    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final WorkerPermissionRepository workerPermissionRepository;
    private final CreatorPermissionRepository creatorPermissionRepository;

    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;
    private final ItemTypePermissionService itemTypePermissionService;
    private final ItemTypeSetPermissionService itemTypeSetPermissionService;

    public ItemTypeConfigurationRemovalImpactDto analyzeRemovalImpact(
            Tenant tenant,
            Long itemTypeSetId,
            Set<Long> removedItemTypeConfigurationIds
    ) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByIdAndTenant(itemTypeSetId, tenant)
                .orElseThrow(() -> new com.example.demo.exception.ApiException("ItemTypeSet not found: " + itemTypeSetId));

        List<ItemTypeConfiguration> configsToRemove = itemTypeSet.getItemTypeConfigurations().stream()
                .filter(config -> removedItemTypeConfigurationIds.contains(config.getId()))
                .collect(Collectors.toList());

        if (configsToRemove.isEmpty()) {
            return ItemTypeConfigurationRemovalImpactDto.builder()
                    .itemTypeSetId(itemTypeSetId)
                    .itemTypeSetName(itemTypeSet.getName())
                    .removedItemTypeConfigurationIds(new ArrayList<>(removedItemTypeConfigurationIds))
                    .removedItemTypeConfigurationNames(getItemTypeConfigurationNames(removedItemTypeConfigurationIds))
                    .affectedItemTypeSets(new ArrayList<>())
                    .fieldOwnerPermissions(new ArrayList<>())
                    .statusOwnerPermissions(new ArrayList<>())
                    .fieldStatusPermissions(new ArrayList<>())
                    .executorPermissions(new ArrayList<>())
                    .workerPermissions(new ArrayList<>())
                    .creatorPermissions(new ArrayList<>())
                    .totalAffectedItemTypeSets(0)
                    .totalFieldOwnerPermissions(0)
                    .totalStatusOwnerPermissions(0)
                    .totalFieldStatusPermissions(0)
                    .totalExecutorPermissions(0)
                    .totalWorkerPermissions(0)
                    .totalCreatorPermissions(0)
                    .totalGrantAssignments(0)
                    .totalRoleAssignments(0)
                    .build();
        }

        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> fieldOwnerPermissions =
                analyzeFieldOwnerPermissionImpacts(configsToRemove, itemTypeSet);

        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> statusOwnerPermissions =
                analyzeStatusOwnerPermissionImpacts(configsToRemove, itemTypeSet);

        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> fieldStatusPermissions =
                analyzeFieldStatusPermissionImpacts(configsToRemove, itemTypeSet);

        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> executorPermissions =
                analyzeExecutorPermissionImpacts(configsToRemove, itemTypeSet);

        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> workerPermissions =
                analyzeWorkerPermissionImpacts(configsToRemove, itemTypeSet);

        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> creatorPermissions =
                analyzeCreatorPermissionImpacts(configsToRemove, itemTypeSet);

        int totalGrantAssignments =
                countGlobalGrantAssignments(fieldOwnerPermissions)
                        + countGlobalGrantAssignments(statusOwnerPermissions)
                        + countGlobalGrantAssignments(fieldStatusPermissions)
                        + countGlobalGrantAssignments(executorPermissions)
                        + countGlobalGrantAssignments(workerPermissions)
                        + countGlobalGrantAssignments(creatorPermissions)
                        + countProjectGrantAssignments(fieldOwnerPermissions)
                        + countProjectGrantAssignments(statusOwnerPermissions)
                        + countProjectGrantAssignments(fieldStatusPermissions)
                        + countProjectGrantAssignments(executorPermissions)
                        + countProjectGrantAssignments(workerPermissions)
                        + countProjectGrantAssignments(creatorPermissions);

        int totalRoleAssignments =
                countGlobalRoleAssignments(fieldOwnerPermissions)
                        + countGlobalRoleAssignments(statusOwnerPermissions)
                        + countGlobalRoleAssignments(fieldStatusPermissions)
                        + countGlobalRoleAssignments(executorPermissions)
                        + countGlobalRoleAssignments(workerPermissions)
                        + countGlobalRoleAssignments(creatorPermissions)
                        + countProjectRoleAssignments(fieldOwnerPermissions)
                        + countProjectRoleAssignments(statusOwnerPermissions)
                        + countProjectRoleAssignments(fieldStatusPermissions)
                        + countProjectRoleAssignments(executorPermissions)
                        + countProjectRoleAssignments(workerPermissions)
                        + countProjectRoleAssignments(creatorPermissions);

        List<ItemTypeConfigurationRemovalImpactDto.ItemTypeSetImpact> affectedItemTypeSets = List.of(
                ItemTypeConfigurationRemovalImpactDto.ItemTypeSetImpact.builder()
                        .itemTypeSetId(itemTypeSet.getId())
                        .itemTypeSetName(itemTypeSet.getName())
                        .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                        .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                        .build()
        );

        return ItemTypeConfigurationRemovalImpactDto.builder()
                .itemTypeSetId(itemTypeSetId)
                .itemTypeSetName(itemTypeSet.getName())
                .removedItemTypeConfigurationIds(new ArrayList<>(removedItemTypeConfigurationIds))
                .removedItemTypeConfigurationNames(getItemTypeConfigurationNames(removedItemTypeConfigurationIds))
                .affectedItemTypeSets(affectedItemTypeSets)
                .fieldOwnerPermissions(fieldOwnerPermissions)
                .statusOwnerPermissions(statusOwnerPermissions)
                .fieldStatusPermissions(fieldStatusPermissions)
                .executorPermissions(executorPermissions)
                .workerPermissions(workerPermissions)
                .creatorPermissions(creatorPermissions)
                .totalAffectedItemTypeSets(affectedItemTypeSets.size())
                .totalFieldOwnerPermissions(fieldOwnerPermissions.size())
                .totalStatusOwnerPermissions(statusOwnerPermissions.size())
                .totalFieldStatusPermissions(fieldStatusPermissions.size())
                .totalExecutorPermissions(executorPermissions.size())
                .totalWorkerPermissions(workerPermissions.size())
                .totalCreatorPermissions(creatorPermissions.size())
                .totalGrantAssignments(totalGrantAssignments)
                .totalRoleAssignments(totalRoleAssignments)
                .build();
    }

    public boolean hasAssignments(ItemTypeConfigurationRemovalImpactDto impact) {
        return (impact.getFieldOwnerPermissions() != null && impact.getFieldOwnerPermissions().stream().anyMatch(PermissionImpact::isHasAssignments))
                || (impact.getStatusOwnerPermissions() != null && impact.getStatusOwnerPermissions().stream().anyMatch(PermissionImpact::isHasAssignments))
                || (impact.getFieldStatusPermissions() != null && impact.getFieldStatusPermissions().stream().anyMatch(PermissionImpact::isHasAssignments))
                || (impact.getExecutorPermissions() != null && impact.getExecutorPermissions().stream().anyMatch(PermissionImpact::isHasAssignments))
                || (impact.getWorkerPermissions() != null && impact.getWorkerPermissions().stream().anyMatch(PermissionImpact::isHasAssignments))
                || (impact.getCreatorPermissions() != null && impact.getCreatorPermissions().stream().anyMatch(PermissionImpact::isHasAssignments));
    }

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

    public void handlePermissionsForNewConfiguration(ItemTypeConfiguration configuration) {
        itemTypePermissionService.createPermissionsForItemTypeConfiguration(configuration);
    }

    public void ensureItemTypeSetPermissions(Long itemTypeSetId, Tenant tenant) {
        itemTypeSetPermissionService.createPermissionsForItemTypeSet(itemTypeSetId, tenant);
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
            deleteProjectAssignments(tenant, itemTypeSet, perm.getId(), "FieldOwnerPermission");
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
            deleteProjectAssignments(tenant, itemTypeSet, perm.getId(), "StatusOwnerPermission");
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
            deleteProjectAssignments(tenant, itemTypeSet, perm.getId(), "FieldStatusPermission");
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
            deleteProjectAssignments(tenant, itemTypeSet, perm.getId(), "ExecutorPermission");
            executorPermissionRepository.delete(perm);
        }
    }

    private boolean isPreserved(Set<Long> preservedPermissionIds, Long permissionId) {
        return preservedPermissionIds != null && preservedPermissionIds.contains(permissionId);
    }

    private void deleteProjectAssignments(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            Long permissionId,
            String permissionType
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

    private List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> analyzeFieldOwnerPermissionImpacts(
            List<ItemTypeConfiguration> configsToRemove,
            ItemTypeSet itemTypeSet
    ) {
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();

        for (ItemTypeConfiguration config : configsToRemove) {
            List<FieldOwnerPermission> permissions = fieldOwnerPermissionRepository.findAllByItemTypeConfiguration(config);

            for (FieldOwnerPermission permission : permissions) {
                AssignmentDetails assignmentDetails = resolveAssignmentDetails(
                        "FieldOwnerPermission",
                        permission.getId(),
                        itemTypeSet
                );

                if (assignmentDetails.hasAssignments()) {
                    impacts.add(ItemTypeConfigurationRemovalImpactDto.PermissionImpact.builder()
                            .permissionId(permission.getId())
                            .permissionType("FIELD_OWNERS")
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .itemTypeConfigurationId(config.getId())
                            .itemTypeName(config.getItemType() != null ? config.getItemType().getName() : null)
                            .itemTypeCategory(config.getCategory() != null ? config.getCategory().toString() : null)
                            .fieldConfigurationId(null)
                            .fieldConfigurationName(permission.getField() != null ? permission.getField().getName() : null)
                            .grantId(assignmentDetails.globalGrantId())
                            .grantName(assignmentDetails.globalGrantName())
                            .assignedRoles(assignmentDetails.assignedRoles())
                            .assignedGrants(assignmentDetails.assignedGrants())
                            .projectAssignedRoles(assignmentDetails.projectRoles())
                            .projectGrants(assignmentDetails.projectGrants())
                            .hasAssignments(true)
                            .build());
                }
            }
        }

        return impacts;
    }

    private List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> analyzeStatusOwnerPermissionImpacts(
            List<ItemTypeConfiguration> configsToRemove,
            ItemTypeSet itemTypeSet
    ) {
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();

        for (ItemTypeConfiguration config : configsToRemove) {
            List<StatusOwnerPermission> permissions = statusOwnerPermissionRepository.findByItemTypeConfigurationIdAndTenant(config.getId(), itemTypeSet.getTenant());

            for (StatusOwnerPermission permission : permissions) {
                AssignmentDetails assignmentDetails = resolveAssignmentDetails(
                        "StatusOwnerPermission",
                        permission.getId(),
                        itemTypeSet
                );

                if (assignmentDetails.hasAssignments()) {
                    impacts.add(ItemTypeConfigurationRemovalImpactDto.PermissionImpact.builder()
                            .permissionId(permission.getId())
                            .permissionType("STATUS_OWNERS")
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .itemTypeConfigurationId(config.getId())
                            .itemTypeName(config.getItemType() != null ? config.getItemType().getName() : null)
                            .itemTypeCategory(config.getCategory() != null ? config.getCategory().toString() : null)
                            .workflowStatusId(permission.getWorkflowStatus() != null ? permission.getWorkflowStatus().getId() : null)
                            .workflowStatusName(permission.getWorkflowStatus() != null && permission.getWorkflowStatus().getStatus() != null
                                    ? permission.getWorkflowStatus().getStatus().getName() : null)
                            .grantId(assignmentDetails.globalGrantId())
                            .grantName(assignmentDetails.globalGrantName())
                            .assignedRoles(assignmentDetails.assignedRoles())
                            .assignedGrants(assignmentDetails.assignedGrants())
                            .projectAssignedRoles(assignmentDetails.projectRoles())
                            .projectGrants(assignmentDetails.projectGrants())
                            .hasAssignments(true)
                            .build());
                }
            }
        }

        return impacts;
    }

    private List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> analyzeFieldStatusPermissionImpacts(
            List<ItemTypeConfiguration> configsToRemove,
            ItemTypeSet itemTypeSet
    ) {
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();

        for (ItemTypeConfiguration config : configsToRemove) {
            List<FieldStatusPermission> permissions = fieldStatusPermissionRepository.findByItemTypeConfigurationIdAndTenant(config.getId(), itemTypeSet.getTenant());

            for (FieldStatusPermission permission : permissions) {
                AssignmentDetails assignmentDetails = resolveAssignmentDetails(
                        "FieldStatusPermission",
                        permission.getId(),
                        itemTypeSet
                );

                if (assignmentDetails.hasAssignments()) {
                    impacts.add(ItemTypeConfigurationRemovalImpactDto.PermissionImpact.builder()
                            .permissionId(permission.getId())
                            .permissionType(permission.getPermissionType() != null ? permission.getPermissionType().toString() : null)
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .itemTypeConfigurationId(config.getId())
                            .itemTypeName(config.getItemType() != null ? config.getItemType().getName() : null)
                            .itemTypeCategory(config.getCategory() != null ? config.getCategory().toString() : null)
                            .fieldConfigurationId(null)
                            .fieldConfigurationName(permission.getField() != null ? permission.getField().getName() : null)
                            .workflowStatusId(permission.getWorkflowStatus() != null ? permission.getWorkflowStatus().getId() : null)
                            .workflowStatusName(permission.getWorkflowStatus() != null && permission.getWorkflowStatus().getStatus() != null
                                    ? permission.getWorkflowStatus().getStatus().getName() : null)
                            .grantId(assignmentDetails.globalGrantId())
                            .grantName(assignmentDetails.globalGrantName())
                            .assignedRoles(assignmentDetails.assignedRoles())
                            .assignedGrants(assignmentDetails.assignedGrants())
                            .projectAssignedRoles(assignmentDetails.projectRoles())
                            .projectGrants(assignmentDetails.projectGrants())
                            .hasAssignments(true)
                            .build());
                }
            }
        }

        return impacts;
    }

    private List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> analyzeExecutorPermissionImpacts(
            List<ItemTypeConfiguration> configsToRemove,
            ItemTypeSet itemTypeSet
    ) {
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();

        for (ItemTypeConfiguration config : configsToRemove) {
            List<ExecutorPermission> permissions = executorPermissionRepository.findAllByItemTypeConfiguration(config);

            for (ExecutorPermission permission : permissions) {
                AssignmentDetails assignmentDetails = resolveAssignmentDetails(
                        "ExecutorPermission",
                        permission.getId(),
                        itemTypeSet
                );

                if (assignmentDetails.hasAssignments() && permission.getTransition() != null) {
                    Transition transition = permission.getTransition();
                    impacts.add(ItemTypeConfigurationRemovalImpactDto.PermissionImpact.builder()
                            .permissionId(permission.getId())
                            .permissionType("EXECUTORS")
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .itemTypeConfigurationId(config.getId())
                            .itemTypeName(config.getItemType() != null ? config.getItemType().getName() : null)
                            .itemTypeCategory(config.getCategory() != null ? config.getCategory().toString() : null)
                            .transitionId(transition.getId())
                            .transitionName(transition.getName())
                            .fromStatusName(transition.getFromStatus() != null && transition.getFromStatus().getStatus() != null
                                    ? transition.getFromStatus().getStatus().getName() : null)
                            .toStatusName(transition.getToStatus() != null && transition.getToStatus().getStatus() != null
                                    ? transition.getToStatus().getStatus().getName() : null)
                            .grantId(assignmentDetails.globalGrantId())
                            .grantName(assignmentDetails.globalGrantName())
                            .assignedRoles(assignmentDetails.assignedRoles())
                            .assignedGrants(assignmentDetails.assignedGrants())
                            .projectAssignedRoles(assignmentDetails.projectRoles())
                            .projectGrants(assignmentDetails.projectGrants())
                            .hasAssignments(true)
                            .build());
                }
            }
        }

        return impacts;
    }

    private List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> analyzeWorkerPermissionImpacts(
            List<ItemTypeConfiguration> configsToRemove,
            ItemTypeSet itemTypeSet
    ) {
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();

        for (ItemTypeConfiguration config : configsToRemove) {
            List<WorkerPermission> permissions = workerPermissionRepository.findAllByItemTypeConfiguration(config);

            for (WorkerPermission permission : permissions) {
                AssignmentDetails assignmentDetails = resolveAssignmentDetails(
                        "WorkerPermission",
                        permission.getId(),
                        itemTypeSet
                );

                if (assignmentDetails.hasAssignments()) {
                    impacts.add(ItemTypeConfigurationRemovalImpactDto.PermissionImpact.builder()
                            .permissionId(permission.getId())
                            .permissionType("WORKERS")
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .itemTypeConfigurationId(config.getId())
                            .itemTypeName(config.getItemType() != null ? config.getItemType().getName() : null)
                            .itemTypeCategory(config.getCategory() != null ? config.getCategory().toString() : null)
                            .grantId(assignmentDetails.globalGrantId())
                            .grantName(assignmentDetails.globalGrantName())
                            .assignedRoles(assignmentDetails.assignedRoles())
                            .assignedGrants(assignmentDetails.assignedGrants())
                            .projectAssignedRoles(assignmentDetails.projectRoles())
                            .projectGrants(assignmentDetails.projectGrants())
                            .hasAssignments(true)
                            .canBePreserved(false)
                            .defaultPreserve(false)
                            .build());
                }
            }
        }

        return impacts;
    }

    private List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> analyzeCreatorPermissionImpacts(
            List<ItemTypeConfiguration> configsToRemove,
            ItemTypeSet itemTypeSet
    ) {
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();

        for (ItemTypeConfiguration config : configsToRemove) {
            List<CreatorPermission> permissions = creatorPermissionRepository.findAllByItemTypeConfiguration(config);

            for (CreatorPermission permission : permissions) {
                AssignmentDetails assignmentDetails = resolveAssignmentDetails(
                        "CreatorPermission",
                        permission.getId(),
                        itemTypeSet
                );

                if (assignmentDetails.hasAssignments()) {
                    impacts.add(ItemTypeConfigurationRemovalImpactDto.PermissionImpact.builder()
                            .permissionId(permission.getId())
                            .permissionType("CREATORS")
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .itemTypeConfigurationId(config.getId())
                            .itemTypeName(config.getItemType() != null ? config.getItemType().getName() : null)
                            .itemTypeCategory(config.getCategory() != null ? config.getCategory().toString() : null)
                            .grantId(assignmentDetails.globalGrantId())
                            .grantName(assignmentDetails.globalGrantName())
                            .assignedRoles(assignmentDetails.assignedRoles())
                            .assignedGrants(assignmentDetails.assignedGrants())
                            .projectAssignedRoles(assignmentDetails.projectRoles())
                            .projectGrants(assignmentDetails.projectGrants())
                            .hasAssignments(true)
                            .canBePreserved(false)
                            .defaultPreserve(false)
                            .build());
                }
            }
        }

        return impacts;
    }

    private AssignmentDetails resolveAssignmentDetails(
            String permissionType,
            Long permissionId,
            ItemTypeSet itemTypeSet
    ) {
        Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                permissionType,
                permissionId,
                itemTypeSet.getTenant()
        );

        List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList()))
                .orElseGet(ArrayList::new);

        List<String> assignedGrants = new ArrayList<>();
        Long globalGrantId = null;
        String globalGrantName = null;
        if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
            Grant grant = assignmentOpt.get().getGrant();
            globalGrantId = grant.getId();
            globalGrantName = grant.getRole() != null
                    ? grant.getRole().getName()
                    : "Grant globale";
            assignedGrants.add(globalGrantName);
        }

        List<ItemTypeConfigurationRemovalImpactDto.ProjectGrantInfo> projectGrants = new ArrayList<>();
        List<ItemTypeConfigurationRemovalImpactDto.ProjectRoleInfo> projectRoles = new ArrayList<>();

        if (itemTypeSet.getProject() != null) {
            appendProjectAssignmentDetails(
                    permissionType,
                    permissionId,
                    itemTypeSet,
                    itemTypeSet.getProject(),
                    projectGrants,
                    projectRoles
            );
        } else if (itemTypeSet.getProjectsAssociation() != null) {
            for (Project project : itemTypeSet.getProjectsAssociation()) {
                appendProjectAssignmentDetails(
                        permissionType,
                        permissionId,
                        itemTypeSet,
                        project,
                        projectGrants,
                        projectRoles
                );
            }
        }

        boolean hasAssignments = !assignedRoles.isEmpty()
                || !assignedGrants.isEmpty()
                || !projectGrants.isEmpty()
                || !projectRoles.isEmpty();

        return new AssignmentDetails(
                assignedRoles,
                assignedGrants,
                globalGrantId,
                globalGrantName,
                projectGrants,
                projectRoles,
                hasAssignments
        );
    }

    private void appendProjectAssignmentDetails(
            String permissionType,
            Long permissionId,
            ItemTypeSet itemTypeSet,
            Project project,
            List<ItemTypeConfigurationRemovalImpactDto.ProjectGrantInfo> projectGrants,
            List<ItemTypeConfigurationRemovalImpactDto.ProjectRoleInfo> projectRoles
    ) {
        if (project == null) {
            return;
        }

        Optional<PermissionAssignment> projectAssignmentOpt =
                projectPermissionAssignmentService.getProjectAssignment(
                        permissionType,
                        permissionId,
                        project.getId(),
                        itemTypeSet.getTenant()
                );

        if (projectAssignmentOpt.isEmpty()) {
            return;
        }

        PermissionAssignment projectAssignment = projectAssignmentOpt.get();
        if (projectAssignment.getGrant() != null) {
            projectGrants.add(ItemTypeConfigurationRemovalImpactDto.ProjectGrantInfo.builder()
                    .projectId(project.getId())
                    .projectName(project.getName())
                    .build());
        }

        Set<Role> projectRolesSet = projectAssignment.getRoles();
        if (projectRolesSet != null && !projectRolesSet.isEmpty()) {
            List<String> projectRoleNames = projectRolesSet.stream()
                    .map(Role::getName)
                    .collect(Collectors.toList());
            projectRoles.add(ItemTypeConfigurationRemovalImpactDto.ProjectRoleInfo.builder()
                    .projectId(project.getId())
                    .projectName(project.getName())
                    .roles(projectRoleNames)
                    .build());
        }
    }

    private record AssignmentDetails(
            List<String> assignedRoles,
            List<String> assignedGrants,
            Long globalGrantId,
            String globalGrantName,
            List<ItemTypeConfigurationRemovalImpactDto.ProjectGrantInfo> projectGrants,
            List<ItemTypeConfigurationRemovalImpactDto.ProjectRoleInfo> projectRoles,
            boolean hasAssignments
    ) {}

    private int countGlobalGrantAssignments(List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> permissions) {
        return permissions.stream()
                .mapToInt(p -> p.getAssignedGrants() != null ? p.getAssignedGrants().size() : 0)
                .sum();
    }

    private int countProjectGrantAssignments(List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> permissions) {
        return permissions.stream()
                .mapToInt(p -> p.getProjectGrants() != null ? p.getProjectGrants().size() : 0)
                .sum();
    }

    private int countGlobalRoleAssignments(List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> permissions) {
        return permissions.stream()
                .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                .sum();
    }

    private int countProjectRoleAssignments(List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> permissions) {
        return permissions.stream()
                .mapToInt(p -> {
                    if (p.getProjectAssignedRoles() == null) {
                        return 0;
                    }
                    return p.getProjectAssignedRoles().stream()
                            .mapToInt(pr -> pr.getRoles() != null ? pr.getRoles().size() : 0)
                            .sum();
                })
                .sum();
    }

    private List<String> getItemTypeConfigurationNames(Set<Long> configIds) {
        return configIds.stream()
                .map(id -> {
                    try {
                        ItemTypeConfiguration config = itemTypeConfigurationRepository.findById(id)
                                .orElse(null);
                        if (config != null && config.getItemType() != null) {
                            return config.getItemType().getName();
                        }
                        return "Configurazione " + id;
                    } catch (Exception e) {
                        return "Configurazione " + id;
                    }
                })
                .collect(Collectors.toList());
    }
}


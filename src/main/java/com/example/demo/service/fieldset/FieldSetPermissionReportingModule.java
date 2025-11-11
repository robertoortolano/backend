package com.example.demo.service.fieldset;

import com.example.demo.dto.FieldSetRemovalImpactDto;
import com.example.demo.dto.FieldSetRemovalImpactDto.PermissionImpact;
import com.example.demo.entity.Field;
import com.example.demo.entity.FieldConfiguration;
import com.example.demo.entity.FieldOwnerPermission;
import com.example.demo.entity.FieldSet;
import com.example.demo.entity.FieldSetEntry;
import com.example.demo.entity.FieldStatusPermission;
import com.example.demo.entity.Grant;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.PermissionAssignment;
import com.example.demo.entity.Project;
import com.example.demo.entity.Role;
import com.example.demo.entity.Status;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.WorkflowStatus;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.FieldOwnerPermissionRepository;
import com.example.demo.repository.FieldSetRepository;
import com.example.demo.repository.FieldStatusPermissionRepository;
import com.example.demo.service.FieldConfigurationLookup;
import com.example.demo.service.ItemTypeSetLookup;
import com.example.demo.service.PermissionAssignmentService;
import com.example.demo.service.ProjectPermissionAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class FieldSetPermissionReportingModule {

    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;
    private final FieldSetRepository fieldSetRepository;
    private final ItemTypeSetLookup itemTypeSetLookup;
    private final FieldConfigurationLookup fieldConfigurationLookup;
    private final FieldSetUpdateHelper fieldSetUpdateHelper;

    @Transactional(readOnly = true)
    public FieldSetRemovalImpactDto analyzeRemovalImpact(
            Tenant tenant,
            Long fieldSetId,
            Set<Long> removedFieldConfigIds,
            Set<Long> addedFieldConfigIds
    ) {
        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(fieldSetId, tenant)
                .orElseThrow(() -> new ApiException("FieldSet not found: " + fieldSetId));

        FieldSetUpdateHelper.FieldRemovalContext removalContext = fieldSetUpdateHelper.computeRemovalContext(
                tenant,
                fieldSet,
                removedFieldConfigIds,
                addedFieldConfigIds
        );

        if (removalContext.removedFieldIds().isEmpty()) {
            return FieldSetRemovalImpactDto.builder()
                    .fieldSetId(fieldSetId)
                    .fieldSetName(fieldSet.getName())
                    .removedFieldConfigurationIds(new ArrayList<>(removedFieldConfigIds))
                    .removedFieldConfigurationNames(fieldSetUpdateHelper.resolveFieldConfigurationNames(removedFieldConfigIds, tenant))
                    .affectedItemTypeSets(new ArrayList<>())
                    .fieldOwnerPermissions(new ArrayList<>())
                    .fieldStatusPermissions(new ArrayList<>())
                    .totalAffectedItemTypeSets(0)
                    .totalFieldOwnerPermissions(0)
                    .totalFieldStatusPermissions(0)
                    .totalGrantAssignments(0)
                    .totalRoleAssignments(0)
                    .build();
        }

        List<ItemTypeSet> allItemTypeSetsUsingFieldSet = itemTypeSetLookup.findByFieldSetId(fieldSetId, tenant);

        List<FieldSetRemovalImpactDto.PermissionImpact> fieldOwnerPermissions =
                analyzeFieldOwnerPermissionImpacts(
                        allItemTypeSetsUsingFieldSet,
                        removalContext.removedFieldIds(),
                        removalContext.remainingFieldIds(),
                        fieldSet
                );

        List<FieldSetRemovalImpactDto.PermissionImpact> fieldStatusPermissions =
                analyzeFieldStatusPermissionImpacts(
                        allItemTypeSetsUsingFieldSet,
                        removalContext.removedFieldIds(),
                        removalContext.remainingFieldIds(),
                        tenant
                );

        Set<Long> itemTypeSetIdsWithImpact = new HashSet<>();
        fieldOwnerPermissions.forEach(p -> itemTypeSetIdsWithImpact.add(p.getItemTypeSetId()));
        fieldStatusPermissions.forEach(p -> itemTypeSetIdsWithImpact.add(p.getItemTypeSetId()));

        List<ItemTypeSet> affectedItemTypeSets = allItemTypeSetsUsingFieldSet.stream()
                .filter(its -> itemTypeSetIdsWithImpact.contains(its.getId()))
                .collect(Collectors.toList());

        int totalGrantAssignments =
                countGlobalGrantAssignments(fieldOwnerPermissions)
                        + countGlobalGrantAssignments(fieldStatusPermissions)
                        + countProjectGrantAssignments(fieldOwnerPermissions)
                        + countProjectGrantAssignments(fieldStatusPermissions);

        int totalRoleAssignments =
                countGlobalRoleAssignments(fieldOwnerPermissions)
                        + countGlobalRoleAssignments(fieldStatusPermissions)
                        + countProjectRoleAssignments(fieldOwnerPermissions)
                        + countProjectRoleAssignments(fieldStatusPermissions);

        List<FieldSetRemovalImpactDto.ItemTypeSetImpact> mappedItemTypeSets =
                mapItemTypeSetImpactsWithAggregates(
                        affectedItemTypeSets,
                        fieldOwnerPermissions,
                        fieldStatusPermissions
                );

        return FieldSetRemovalImpactDto.builder()
                .fieldSetId(fieldSetId)
                .fieldSetName(fieldSet.getName())
                .removedFieldConfigurationIds(new ArrayList<>(removedFieldConfigIds))
                .removedFieldConfigurationNames(fieldSetUpdateHelper.resolveFieldConfigurationNames(removedFieldConfigIds, tenant))
                .affectedItemTypeSets(mappedItemTypeSets)
                .fieldOwnerPermissions(fieldOwnerPermissions)
                .fieldStatusPermissions(fieldStatusPermissions)
                .totalAffectedItemTypeSets(affectedItemTypeSets.size())
                .totalFieldOwnerPermissions(fieldOwnerPermissions.size())
                .totalFieldStatusPermissions(fieldStatusPermissions.size())
                .totalGrantAssignments(totalGrantAssignments)
                .totalRoleAssignments(totalRoleAssignments)
                .build();
    }

    public boolean hasAssignments(FieldSetRemovalImpactDto impact) {
        boolean ownerHasAssignments = impact.getFieldOwnerPermissions() != null
                && impact.getFieldOwnerPermissions().stream().anyMatch(PermissionImpact::isHasAssignments);

        boolean statusHasAssignments = impact.getFieldStatusPermissions() != null
                && impact.getFieldStatusPermissions().stream().anyMatch(PermissionImpact::isHasAssignments);

        return ownerHasAssignments || statusHasAssignments;
    }

    private List<FieldSetRemovalImpactDto.PermissionImpact> analyzeFieldOwnerPermissionImpacts(
            List<ItemTypeSet> itemTypeSets,
            Set<Long> removedFieldIds,
            Set<Long> remainingFieldIds,
            FieldSet fieldSet
    ) {
        List<FieldSetRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();

        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                List<FieldOwnerPermission> allPermissions = fieldOwnerPermissionRepository
                        .findAllByItemTypeConfiguration(config);

                for (FieldOwnerPermission permission : allPermissions) {
                    Long fieldId = permission.getField().getId();

                    boolean isRemoved = removedFieldIds.contains(fieldId);
                    boolean isOrphaned = !remainingFieldIds.contains(fieldId);

                    if (isRemoved || isOrphaned) {
                        Field field = permission.getField();
                        AssignmentDetails assignmentDetails = resolveAssignmentDetails(
                                "FieldOwnerPermission",
                                permission.getId(),
                                itemTypeSet
                        );

                        if (assignmentDetails.hasAssignments()) {
                            FieldConfiguration targetConfig = findTargetConfiguration(fieldSet, fieldId);
                            FieldConfiguration fallbackConfig = null;
                            try {
                                fallbackConfig = fieldConfigurationLookup.getAllByField(fieldId, itemTypeSet.getTenant())
                                        .stream()
                                        .findFirst()
                                        .orElse(null);
                            } catch (Exception ignored) {
                                // Gestione fallback sotto
                            }

                            Long fieldConfigurationId = targetConfig != null
                                    ? targetConfig.getId()
                                    : (fallbackConfig != null ? fallbackConfig.getId() : null);
                            String fieldConfigurationName = targetConfig != null
                                    ? targetConfig.getName()
                                    : (fallbackConfig != null ? fallbackConfig.getName() : field.getName());

                            boolean canBePreserved = remainingFieldIds.contains(fieldId);
                            boolean defaultPreserve = canBePreserved && assignmentDetails.hasAssignments();

                            impacts.add(FieldSetRemovalImpactDto.PermissionImpact.builder()
                                    .permissionId(permission.getId())
                                    .permissionType("FIELD_OWNERS")
                                    .itemTypeSetId(itemTypeSet.getId())
                                    .itemTypeSetName(itemTypeSet.getName())
                                    .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                                    .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                                    .fieldConfigurationId(fieldConfigurationId)
                                    .fieldConfigurationName(fieldConfigurationName)
                                    .fieldId(fieldId)
                                    .fieldName(field.getName())
                                    .matchingFieldId(canBePreserved ? fieldId : null)
                                    .matchingFieldName(canBePreserved ? field.getName() : null)
                                    .assignedRoles(assignmentDetails.assignedRoles())
                                    .assignedGrants(assignmentDetails.assignedGrants())
                                    .projectAssignedRoles(assignmentDetails.projectRoles())
                                    .hasAssignments(true)
                                    .canBePreserved(canBePreserved)
                                    .defaultPreserve(defaultPreserve)
                                    .grantId(assignmentDetails.globalGrantId())
                                    .grantName(assignmentDetails.globalGrantName())
                                    .projectGrants(assignmentDetails.projectGrants())
                                    .build());
                        }
                    }
                }
            }
        }

        return impacts;
    }

    private FieldConfiguration findTargetConfiguration(FieldSet fieldSet, Long fieldId) {
        for (FieldSetEntry entry : fieldSet.getFieldSetEntries()) {
            if (entry.getFieldConfiguration().getField().getId().equals(fieldId)) {
                return entry.getFieldConfiguration();
            }
        }
        return null;
    }

    private List<FieldSetRemovalImpactDto.PermissionImpact> analyzeFieldStatusPermissionImpacts(
            List<ItemTypeSet> itemTypeSets,
            Set<Long> removedFieldIds,
            Set<Long> remainingFieldIds,
            Tenant tenant
    ) {
        List<FieldSetRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();

        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                List<FieldStatusPermission> allPermissions = fieldStatusPermissionRepository
                        .findAllByItemTypeConfiguration(config);

                for (FieldStatusPermission permission : allPermissions) {
                    Long fieldId = permission.getField().getId();

                    boolean isRemoved = removedFieldIds.contains(fieldId);
                    boolean isOrphaned = !remainingFieldIds.contains(fieldId);

                    if (isRemoved || isOrphaned) {
                        Field field = permission.getField();
                        WorkflowStatus workflowStatus = permission.getWorkflowStatus();
                        AssignmentDetails assignmentDetails = resolveAssignmentDetails(
                                "FieldStatusPermission",
                                permission.getId(),
                                itemTypeSet
                        );

                        if (assignmentDetails.hasAssignments()) {
                            FieldConfiguration exampleConfig = null;
                            try {
                                exampleConfig = fieldConfigurationLookup.getAllByField(fieldId, tenant)
                                        .stream()
                                        .findFirst()
                                        .orElse(null);
                            } catch (Exception ignored) {
                                // Gestione fallback sotto
                            }

                            Status status = workflowStatus.getStatus();
                            boolean fieldRemains = remainingFieldIds.contains(fieldId);
                            boolean statusRemains = true;
                            boolean canBePreserved = fieldRemains && statusRemains;
                            boolean defaultPreserve = canBePreserved && assignmentDetails.hasAssignments();

                            String permissionType = permission.getPermissionType() == FieldStatusPermission.PermissionType.EDITORS
                                    ? "FIELD_EDITORS"
                                    : "FIELD_VIEWERS";

                            Long primaryProjectId = null;
                            String primaryProjectName = null;
                            if (itemTypeSet.getProject() != null) {
                                primaryProjectId = itemTypeSet.getProject().getId();
                                primaryProjectName = itemTypeSet.getProject().getName();
                            } else if (!itemTypeSet.getProjectsAssociation().isEmpty()) {
                                Project firstProject = itemTypeSet.getProjectsAssociation().iterator().next();
                                primaryProjectId = firstProject.getId();
                                primaryProjectName = firstProject.getName();
                            }

                            impacts.add(FieldSetRemovalImpactDto.PermissionImpact.builder()
                                    .permissionId(permission.getId())
                                    .permissionType(permissionType)
                                    .itemTypeSetId(itemTypeSet.getId())
                                    .itemTypeSetName(itemTypeSet.getName())
                                    .projectId(primaryProjectId)
                                    .projectName(primaryProjectName)
                                    .fieldConfigurationId(exampleConfig != null ? exampleConfig.getId() : null)
                                    .fieldConfigurationName(exampleConfig != null ? exampleConfig.getName() : field.getName())
                                    .workflowStatusId(workflowStatus.getId())
                                    .workflowStatusName(workflowStatus.getStatus().getName())
                                    .fieldId(fieldId)
                                    .fieldName(field.getName())
                                    .statusId(status != null ? status.getId() : null)
                                    .statusName(status != null ? status.getName() : null)
                                    .matchingFieldId(canBePreserved && fieldRemains ? fieldId : null)
                                    .matchingFieldName(canBePreserved && fieldRemains ? field.getName() : null)
                                    .matchingStatusId(canBePreserved && statusRemains ? (status != null ? status.getId() : null) : null)
                                    .matchingStatusName(canBePreserved && statusRemains ? (status != null ? status.getName() : null) : null)
                                    .assignedRoles(assignmentDetails.assignedRoles())
                                    .assignedGrants(assignmentDetails.assignedGrants())
                                    .projectAssignedRoles(assignmentDetails.projectRoles())
                                    .hasAssignments(true)
                                    .canBePreserved(canBePreserved)
                                    .defaultPreserve(defaultPreserve)
                                    .grantId(assignmentDetails.globalGrantId())
                                    .grantName(assignmentDetails.globalGrantName())
                                    .projectGrants(assignmentDetails.projectGrants())
                                    .build());
                        }
                    }
                }
            }
        }

        return impacts;
    }

    private List<FieldSetRemovalImpactDto.ItemTypeSetImpact> mapItemTypeSetImpactsWithAggregates(
            List<ItemTypeSet> itemTypeSets,
            List<FieldSetRemovalImpactDto.PermissionImpact> fieldOwnerPermissions,
            List<FieldSetRemovalImpactDto.PermissionImpact> fieldStatusPermissions
    ) {
        return itemTypeSets.stream()
                .map(its -> {
                    Long itemTypeSetId = its.getId();

                    List<FieldSetRemovalImpactDto.PermissionImpact> itsPermissions = new ArrayList<>();
                    itsPermissions.addAll(fieldOwnerPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList()));
                    itsPermissions.addAll(fieldStatusPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList()));

                    int totalPermissions = itsPermissions.size();
                    int totalGlobalRoles = itsPermissions.stream()
                            .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                            .sum();
                    int totalProjectRoles = itsPermissions.stream()
                            .mapToInt(p -> {
                                if (p.getProjectAssignedRoles() == null) {
                                    return 0;
                                }
                                return p.getProjectAssignedRoles().stream()
                                        .mapToInt(pr -> pr.getRoles() != null ? pr.getRoles().size() : 0)
                                        .sum();
                            })
                            .sum();
                    int totalRoleAssignments = totalGlobalRoles + totalProjectRoles;
                    int totalGlobalGrants = itsPermissions.stream()
                            .mapToInt(p -> p.getAssignedGrants() != null ? p.getAssignedGrants().size() : 0)
                            .sum();

                    Map<Long, Integer> projectGrantsCount = new HashMap<>();
                    for (FieldSetRemovalImpactDto.PermissionImpact perm : itsPermissions) {
                        if (perm.getProjectGrants() != null) {
                            for (FieldSetRemovalImpactDto.ProjectGrantInfo pg : perm.getProjectGrants()) {
                                projectGrantsCount.merge(pg.getProjectId(), 1, Integer::sum);
                            }
                        }
                    }
                    int totalProjectGrants = projectGrantsCount.values().stream().mapToInt(Integer::intValue).sum();
                    List<FieldSetRemovalImpactDto.ProjectImpact> projectImpacts = projectGrantsCount.entrySet().stream()
                            .map(e -> {
                                String projectName = its.getProject() != null && its.getProject().getId().equals(e.getKey())
                                        ? its.getProject().getName()
                                        : its.getProjectsAssociation().stream()
                                        .filter(p -> p.getId().equals(e.getKey()))
                                        .findFirst()
                                        .map(Project::getName)
                                        .orElse("Progetto " + e.getKey());
                                return FieldSetRemovalImpactDto.ProjectImpact.builder()
                                        .projectId(e.getKey())
                                        .projectName(projectName)
                                        .projectGrantsCount(e.getValue())
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return FieldSetRemovalImpactDto.ItemTypeSetImpact.builder()
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

    private int countGlobalGrantAssignments(List<FieldSetRemovalImpactDto.PermissionImpact> permissions) {
        return permissions.stream()
                .mapToInt(p -> p.getAssignedGrants() != null ? p.getAssignedGrants().size() : 0)
                .sum();
    }

    private int countProjectGrantAssignments(List<FieldSetRemovalImpactDto.PermissionImpact> permissions) {
        return permissions.stream()
                .mapToInt(p -> p.getProjectGrants() != null ? p.getProjectGrants().size() : 0)
                .sum();
    }

    private int countGlobalRoleAssignments(List<FieldSetRemovalImpactDto.PermissionImpact> permissions) {
        return permissions.stream()
                .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                .sum();
    }

    private int countProjectRoleAssignments(List<FieldSetRemovalImpactDto.PermissionImpact> permissions) {
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

        List<FieldSetRemovalImpactDto.ProjectGrantInfo> projectGrants = new ArrayList<>();
        List<FieldSetRemovalImpactDto.ProjectRoleInfo> projectRoles = new ArrayList<>();

        Set<Project> projectsToCheck = new HashSet<>();
        if (itemTypeSet.getProject() != null) {
            projectsToCheck.add(itemTypeSet.getProject());
        }
        if (itemTypeSet.getProjectsAssociation() != null) {
            projectsToCheck.addAll(itemTypeSet.getProjectsAssociation());
        }

        for (Project project : projectsToCheck) {
            Optional<PermissionAssignment> projectAssignmentOpt =
                    projectPermissionAssignmentService.getProjectAssignment(
                            permissionType,
                            permissionId,
                            project.getId(),
                            itemTypeSet.getTenant()
                    );

            if (projectAssignmentOpt.isEmpty()) {
                continue;
            }

            PermissionAssignment projectAssignment = projectAssignmentOpt.get();
            if (projectAssignment.getGrant() != null) {
                projectGrants.add(FieldSetRemovalImpactDto.ProjectGrantInfo.builder()
                        .projectId(project.getId())
                        .projectName(project.getName())
                        .build());
            }

            Set<Role> projectRolesSet = projectAssignment.getRoles();
            if (projectRolesSet != null && !projectRolesSet.isEmpty()) {
                List<String> roleNames = projectRolesSet.stream()
                        .map(Role::getName)
                        .collect(Collectors.toList());
                projectRoles.add(FieldSetRemovalImpactDto.ProjectRoleInfo.builder()
                        .projectId(project.getId())
                        .projectName(project.getName())
                        .roles(roleNames)
                        .build());
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

    private record AssignmentDetails(
            List<String> assignedRoles,
            List<String> assignedGrants,
            Long globalGrantId,
            String globalGrantName,
            List<FieldSetRemovalImpactDto.ProjectGrantInfo> projectGrants,
            List<FieldSetRemovalImpactDto.ProjectRoleInfo> projectRoles,
            boolean hasAssignments
    ) {
    }
}
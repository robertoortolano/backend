package com.example.demo.service;

import com.example.demo.dto.ItemTypeConfigurationMigrationImpactDto;
import com.example.demo.entity.CreatorPermission;
import com.example.demo.entity.ExecutorPermission;
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
import com.example.demo.entity.StatusOwnerPermission;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Transition;
import com.example.demo.entity.Workflow;
import com.example.demo.entity.WorkflowStatus;
import com.example.demo.entity.WorkerPermission;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.CreatorPermissionRepository;
import com.example.demo.repository.ExecutorPermissionRepository;
import com.example.demo.repository.FieldOwnerPermissionRepository;
import com.example.demo.repository.FieldStatusPermissionRepository;
import com.example.demo.repository.ItemTypeConfigurationRepository;
import com.example.demo.repository.ItemTypeSetRepository;
import com.example.demo.repository.StatusOwnerPermissionRepository;
import com.example.demo.repository.TransitionRepository;
import com.example.demo.repository.WorkflowStatusRepository;
import com.example.demo.repository.WorkerPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemTypeConfigurationMigrationAnalysisService {

    private final ItemTypeConfigurationRepository itemTypeConfigurationRepository;
    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final WorkerPermissionRepository workerPermissionRepository;
    private final CreatorPermissionRepository creatorPermissionRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final TransitionRepository transitionRepository;
    private final FieldLookup fieldLookup;
    private final WorkflowLookup workflowLookup;
    private final FieldSetLookup fieldSetLookup;
    private final ItemTypeSetRepository itemTypeSetRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;

    public ItemTypeConfigurationMigrationImpactDto analyzeMigrationImpact(
            Tenant tenant,
            Long itemTypeConfigurationId,
            Long newFieldSetId,
            Long newWorkflowId
    ) {
        ItemTypeConfiguration oldConfig = itemTypeConfigurationRepository.findById(itemTypeConfigurationId)
                .orElseThrow(() -> new ApiException("ItemTypeConfiguration not found: " + itemTypeConfigurationId));

        if (!oldConfig.getTenant().getId().equals(tenant.getId())) {
            throw new ApiException("ItemTypeConfiguration does not belong to tenant");
        }

        FieldSet newFieldSet = null;
        Workflow newWorkflow = null;

        if (newFieldSetId != null) {
            newFieldSet = fieldSetLookup.getById(newFieldSetId, tenant);
        }

        if (newWorkflowId != null) {
            newWorkflow = workflowLookup.getByIdEntity(tenant, newWorkflowId);
        }

        FieldSet oldFieldSet = oldConfig.getFieldSet();
        Workflow oldWorkflow = oldConfig.getWorkflow();

        boolean fieldSetChanged = newFieldSet != null && !oldFieldSet.getId().equals(newFieldSet.getId());
        boolean workflowChanged = newWorkflow != null && !oldWorkflow.getId().equals(newWorkflow.getId());

        if (!fieldSetChanged && !workflowChanged) {
            throw new ApiException("No changes detected. FieldSet and Workflow are the same.");
        }

        ItemTypeConfigurationMigrationImpactDto.FieldSetInfo oldFieldSetInfo = extractFieldSetInfo(oldFieldSet);
        ItemTypeConfigurationMigrationImpactDto.FieldSetInfo newFieldSetInfo = newFieldSet != null
                ? extractFieldSetInfo(newFieldSet)
                : oldFieldSetInfo;

        ItemTypeConfigurationMigrationImpactDto.WorkflowInfo oldWorkflowInfo = extractWorkflowInfo(oldWorkflow);
        ItemTypeConfigurationMigrationImpactDto.WorkflowInfo newWorkflowInfo = newWorkflow != null
                ? extractWorkflowInfo(newWorkflow)
                : oldWorkflowInfo;

        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> fieldOwnerPermissions =
                analyzeFieldOwnerPermissions(oldConfig, oldFieldSetInfo, newFieldSetInfo, fieldSetChanged);

        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> statusOwnerPermissions =
                analyzeStatusOwnerPermissions(oldConfig, oldWorkflowInfo, newWorkflowInfo, workflowChanged);

        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> fieldStatusPermissions =
                analyzeFieldStatusPermissions(oldConfig, oldFieldSetInfo, newFieldSetInfo, oldWorkflowInfo, newWorkflowInfo, fieldSetChanged, workflowChanged);

        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> executorPermissions =
                analyzeExecutorPermissions(oldConfig, oldWorkflowInfo, newWorkflowInfo, workflowChanged);

        Long itemTypeSetId = getItemTypeSetIdForConfiguration(oldConfig);
        ItemTypeSet owningItemTypeSet = itemTypeSetId != null
                ? itemTypeSetRepository.findById(itemTypeSetId).orElse(null)
                : null;

        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> workerPermissions =
                analyzeWorkerPermissions(oldConfig, owningItemTypeSet);

        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> creatorPermissions =
                analyzeCreatorPermissions(oldConfig, owningItemTypeSet);

        int totalPreservable = countPreservable(fieldOwnerPermissions, statusOwnerPermissions, fieldStatusPermissions, executorPermissions, workerPermissions, creatorPermissions);
        int totalRemovable = countRemovable(fieldOwnerPermissions, statusOwnerPermissions, fieldStatusPermissions, executorPermissions, workerPermissions, creatorPermissions);
        int totalNew = countNew(fieldOwnerPermissions, statusOwnerPermissions, fieldStatusPermissions, executorPermissions, workerPermissions, creatorPermissions);
        int totalWithRoles = countWithRoles(fieldOwnerPermissions, statusOwnerPermissions, fieldStatusPermissions, executorPermissions, workerPermissions, creatorPermissions);

        String itemTypeSetName = getItemTypeSetNameForConfiguration(oldConfig);

        return ItemTypeConfigurationMigrationImpactDto.builder()
                .itemTypeConfigurationId(oldConfig.getId())
                .itemTypeConfigurationName(oldConfig.getItemType().getName() + " Configuration")
                .itemTypeSetId(itemTypeSetId)
                .itemTypeSetName(itemTypeSetName)
                .itemTypeId(oldConfig.getItemType().getId())
                .itemTypeName(oldConfig.getItemType().getName())
                .oldFieldSet(oldFieldSetInfo)
                .newFieldSet(newFieldSetInfo)
                .fieldSetChanged(fieldSetChanged)
                .oldWorkflow(oldWorkflowInfo)
                .newWorkflow(newWorkflowInfo)
                .workflowChanged(workflowChanged)
                .fieldOwnerPermissions(fieldOwnerPermissions)
                .statusOwnerPermissions(statusOwnerPermissions)
                .fieldStatusPermissions(fieldStatusPermissions)
                .executorPermissions(executorPermissions)
                .workerPermissions(workerPermissions)
                .creatorPermissions(creatorPermissions)
                .totalPreservablePermissions(totalPreservable)
                .totalRemovablePermissions(totalRemovable)
                .totalNewPermissions(totalNew)
                .totalPermissionsWithRoles(totalWithRoles)
                .build();
    }

    private ItemTypeConfigurationMigrationImpactDto.FieldSetInfo extractFieldSetInfo(FieldSet fieldSet) {
        if (fieldSet == null) {
            return null;
        }

        Set<Long> fieldIds = fieldSet.getFieldSetEntries().stream()
                .map(entry -> entry.getFieldConfiguration().getField().getId())
                .collect(Collectors.toSet());

        List<ItemTypeConfigurationMigrationImpactDto.FieldInfo> fields = fieldIds.stream()
                .map(fieldId -> {
                    var field = fieldLookup.getById(fieldId, fieldSet.getTenant());
                    return ItemTypeConfigurationMigrationImpactDto.FieldInfo.builder()
                            .fieldId(field.getId())
                            .fieldName(field.getName())
                            .build();
                })
                .collect(Collectors.toList());

        return ItemTypeConfigurationMigrationImpactDto.FieldSetInfo.builder()
                .fieldSetId(fieldSet.getId())
                .fieldSetName(fieldSet.getName())
                .fields(fields)
                .build();
    }

    private ItemTypeConfigurationMigrationImpactDto.WorkflowInfo extractWorkflowInfo(Workflow workflow) {
        if (workflow == null) {
            return null;
        }

        List<WorkflowStatus> statuses = workflowStatusRepository.findAllByWorkflowId(workflow.getId());
        List<ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo> workflowStatusInfos = statuses.stream()
                .map(ws -> {
                    Status status = ws.getStatus();
                    if (status == null) {
                        log.warn("WorkflowStatus {} has null Status!", ws.getId());
                        return null;
                    }
                    Long statusId = status.getId();
                    String statusName = status.getName();

                    return ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo.builder()
                            .workflowStatusId(ws.getId())
                            .workflowStatusName(statusName)
                            .statusId(statusId)
                            .statusName(statusName)
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        List<Transition> transitions = transitionRepository.findByWorkflowAndTenant(workflow, workflow.getTenant());
        List<ItemTypeConfigurationMigrationImpactDto.TransitionInfo> transitionInfos = transitions.stream()
                .map(t -> ItemTypeConfigurationMigrationImpactDto.TransitionInfo.builder()
                        .transitionId(t.getId())
                        .transitionName(t.getName())
                        .fromWorkflowStatusId(t.getFromStatus().getId())
                        .fromWorkflowStatusName(t.getFromStatus().getStatus().getName())
                        .toWorkflowStatusId(t.getToStatus().getId())
                        .toWorkflowStatusName(t.getToStatus().getStatus().getName())
                        .build())
                .collect(Collectors.toList());

        return ItemTypeConfigurationMigrationImpactDto.WorkflowInfo.builder()
                .workflowId(workflow.getId())
                .workflowName(workflow.getName())
                .workflowStatuses(workflowStatusInfos)
                .transitions(transitionInfos)
                .build();
    }

    private List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyzeFieldOwnerPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto.FieldSetInfo oldFieldSetInfo,
            ItemTypeConfigurationMigrationImpactDto.FieldSetInfo newFieldSetInfo,
            boolean fieldSetChanged
    ) {
        if (!fieldSetChanged) {
            return Collections.emptyList();
        }

        List<FieldOwnerPermission> existingPermissions = fieldOwnerPermissionRepository
                .findAllByItemTypeConfiguration(config);

        Set<Long> newFieldIds = newFieldSetInfo.getFields().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.FieldInfo::getFieldId)
                .collect(Collectors.toSet());

        Map<Long, ItemTypeConfigurationMigrationImpactDto.FieldInfo> newFieldsMap = newFieldSetInfo.getFields().stream()
                .collect(Collectors.toMap(
                        ItemTypeConfigurationMigrationImpactDto.FieldInfo::getFieldId,
                        java.util.function.Function.identity()
                ));

        Long itemTypeSetId = getItemTypeSetIdForConfiguration(config);
        Tenant tenant = config.getTenant();
        FieldSet oldFieldSet = config.getFieldSet();

        return existingPermissions.stream()
                .map(perm -> {
                    Long fieldId = perm.getField().getId();
                    boolean canPreserve = newFieldIds.contains(fieldId);
                    ItemTypeConfigurationMigrationImpactDto.FieldInfo matchingField = canPreserve
                            ? newFieldsMap.get(fieldId)
                            : null;

                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "FieldOwnerPermission", perm.getId(), tenant);
                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());

                    boolean hasRoles = !assignedRoles.isEmpty();

                    Long grantId = null;
                    String grantName = null;
                    List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrantsList = new ArrayList<>();

                    if (itemTypeSetId != null) {
                        FieldConfiguration fieldConfig = oldFieldSet.getFieldSetEntries().stream()
                                .map(FieldSetEntry::getFieldConfiguration)
                                .filter(fc -> fc.getField().getId().equals(fieldId))
                                .findFirst()
                                .orElse(null);

                        if (fieldConfig != null) {
                            if (assignmentOpt.isPresent()) {
                                PermissionAssignment assignment = assignmentOpt.get();
                                if (assignment.getGrant() != null) {
                                    Grant grant = assignment.getGrant();
                                    grantId = grant.getId();
                                    grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
                                }
                            }

                            ItemTypeSet itemTypeSet = itemTypeSetRepository.findById(itemTypeSetId)
                                    .orElse(null);
                            if (itemTypeSet != null) {
                                if (itemTypeSet.getProject() != null) {
                                    Optional<PermissionAssignment> projectAssignmentOpt =
                                            projectPermissionAssignmentService.getProjectAssignment(
                                                    "FieldOwnerPermission", perm.getId(),
                                                    itemTypeSet.getProject().getId(), tenant);
                                    projectAssignmentOpt.ifPresent(projectAssignment -> {
                                        if (projectAssignment.getGrant() != null) {
                                            projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                                    .projectId(itemTypeSet.getProject().getId())
                                                    .projectName(itemTypeSet.getProject().getName())
                                                    .build());
                                        }
                                    });
                                } else {
                                    for (Project project : itemTypeSet.getProjectsAssociation()) {
                                        Optional<PermissionAssignment> projectAssignmentOpt =
                                                projectPermissionAssignmentService.getProjectAssignment(
                                                        "FieldOwnerPermission", perm.getId(),
                                                        project.getId(), tenant);
                                        projectAssignmentOpt.ifPresent(projectAssignment -> {
                                            if (projectAssignment.getGrant() != null) {
                                                projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                                        .projectId(project.getId())
                                                        .projectName(project.getName())
                                                        .build());
                                            }
                                        });
                                    }
                                }
                            }
                        }
                    }

                    boolean hasAssignments = hasRoles || grantId != null || !projectGrantsList.isEmpty();
                    boolean defaultPreserve = canPreserve && hasAssignments;

                    return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                            .permissionId(perm.getId())
                            .permissionType("FIELD_OWNERS")
                            .entityId(fieldId)
                            .entityName(perm.getField().getName())
                            .matchingEntityId(canPreserve && matchingField != null ? matchingField.getFieldId() : null)
                            .matchingEntityName(canPreserve && matchingField != null ? matchingField.getFieldName() : null)
                            .assignedRoles(assignedRoles)
                            .hasAssignments(hasAssignments)
                            .canBePreserved(canPreserve)
                            .defaultPreserve(defaultPreserve)
                            .suggestedAction(canPreserve ? "PRESERVE" : "REMOVE")
                            .itemTypeSetId(itemTypeSetId)
                            .itemTypeSetName(getItemTypeSetNameForConfiguration(config))
                            .projectId(config.getProject() != null ? config.getProject().getId() : null)
                            .projectName(config.getProject() != null ? config.getProject().getName() : null)
                            .grantId(grantId)
                            .grantName(grantName)
                            .projectGrants(projectGrantsList)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyzeStatusOwnerPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto.WorkflowInfo oldWorkflowInfo,
            ItemTypeConfigurationMigrationImpactDto.WorkflowInfo newWorkflowInfo,
            boolean workflowChanged
    ) {
        if (!workflowChanged) {
            return Collections.emptyList();
        }

        List<StatusOwnerPermission> existingPermissions = statusOwnerPermissionRepository
                .findAllByItemTypeConfiguration(config);

        Set<Long> newStatusIds = newWorkflowInfo.getWorkflowStatuses().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo::getStatusId)
                .collect(Collectors.toSet());

        Map<Long, ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo> newStatusesMap = newWorkflowInfo.getWorkflowStatuses().stream()
                .collect(Collectors.toMap(
                        ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo::getStatusId,
                        java.util.function.Function.identity(),
                        (existing, replacement) -> existing
                ));

        Long itemTypeSetId = getItemTypeSetIdForConfiguration(config);
        Tenant tenant = config.getTenant();

        return existingPermissions.stream()
                .map(perm -> {
                    Status status = perm.getWorkflowStatus().getStatus();
                    Long statusId = status.getId();
                    String statusName = status.getName();

                    boolean canPreserve = newStatusIds.contains(statusId);
                    ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo matchingStatus = canPreserve
                            ? newStatusesMap.get(statusId)
                            : null;

                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "StatusOwnerPermission", perm.getId(), tenant);
                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());

                    boolean hasRoles = !assignedRoles.isEmpty();

                    Long grantId = null;
                    String grantName = null;
                    List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrantsList = new ArrayList<>();

                    if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                        Grant grant = assignmentOpt.get().getGrant();
                        grantId = grant.getId();
                        grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
                    }

                    ItemTypeSet itemTypeSet = itemTypeSetId != null ? itemTypeSetRepository.findById(itemTypeSetId).orElse(null) : null;
                    if (itemTypeSet != null) {
                        if (itemTypeSet.getProject() != null) {
                            Optional<PermissionAssignment> projectAssignmentOpt =
                                    projectPermissionAssignmentService.getProjectAssignment(
                                            "StatusOwnerPermission", perm.getId(),
                                            itemTypeSet.getProject().getId(), tenant);
                            projectAssignmentOpt.ifPresent(projectAssignment -> {
                                if (projectAssignment.getGrant() != null) {
                                    projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                            .projectId(itemTypeSet.getProject().getId())
                                            .projectName(itemTypeSet.getProject().getName())
                                            .build());
                                }
                            });
                        } else {
                            for (Project project : itemTypeSet.getProjectsAssociation()) {
                                Optional<PermissionAssignment> projectAssignmentOpt =
                                        projectPermissionAssignmentService.getProjectAssignment(
                                                "StatusOwnerPermission", perm.getId(), project.getId(), tenant);
                                projectAssignmentOpt.ifPresent(projectAssignment -> {
                                    if (projectAssignment.getGrant() != null) {
                                        projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                                .projectId(project.getId())
                                                .projectName(project.getName())
                                                .build());
                                    }
                                });
                            }
                        }
                    }

                    boolean hasAssignments = hasRoles || grantId != null || !projectGrantsList.isEmpty();
                    boolean defaultPreserve = canPreserve && hasAssignments;

                    return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                            .permissionId(perm.getId())
                            .permissionType("STATUS_OWNERS")
                            .entityId(statusId)
                            .entityName(statusName)
                            .matchingEntityId(canPreserve && matchingStatus != null ? matchingStatus.getStatusId() : null)
                            .matchingEntityName(canPreserve && matchingStatus != null ? matchingStatus.getStatusName() : null)
                            .assignedRoles(assignedRoles)
                            .hasAssignments(hasAssignments)
                            .canBePreserved(canPreserve)
                            .defaultPreserve(defaultPreserve)
                            .suggestedAction(canPreserve ? "PRESERVE" : "REMOVE")
                            .itemTypeSetId(itemTypeSetId)
                            .itemTypeSetName(getItemTypeSetNameForConfiguration(config))
                            .projectId(itemTypeSet != null && itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet != null && itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .grantId(grantId)
                            .grantName(grantName)
                            .projectGrants(projectGrantsList)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyzeFieldStatusPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto.FieldSetInfo oldFieldSetInfo,
            ItemTypeConfigurationMigrationImpactDto.FieldSetInfo newFieldSetInfo,
            ItemTypeConfigurationMigrationImpactDto.WorkflowInfo oldWorkflowInfo,
            ItemTypeConfigurationMigrationImpactDto.WorkflowInfo newWorkflowInfo,
            boolean fieldSetChanged,
            boolean workflowChanged
    ) {
        if (!fieldSetChanged && !workflowChanged) {
            return Collections.emptyList();
        }

        List<FieldStatusPermission> existingPermissions = fieldStatusPermissionRepository
                .findAllByItemTypeConfiguration(config);

        Set<Long> newFieldIds = newFieldSetInfo.getFields().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.FieldInfo::getFieldId)
                .collect(Collectors.toSet());

        Set<Long> newStatusIds = newWorkflowInfo.getWorkflowStatuses().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo::getStatusId)
                .collect(Collectors.toSet());

        Map<Long, ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo> newStatusesMap = newWorkflowInfo.getWorkflowStatuses().stream()
                .collect(Collectors.toMap(
                        ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo::getStatusId,
                        java.util.function.Function.identity(),
                        (existing, replacement) -> existing
                ));

        Long itemTypeSetId = getItemTypeSetIdForConfiguration(config);
        Tenant tenant = config.getTenant();

        return existingPermissions.stream()
                .map(perm -> {
                    Long fieldId = perm.getField().getId();
                    Long statusId = perm.getWorkflowStatus().getStatus().getId();

                    boolean fieldExists = newFieldIds.contains(fieldId);
                    boolean statusExists = newStatusIds.contains(statusId);
                    boolean canPreserve = fieldExists && statusExists;

                    ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo matchingStatus = statusExists ? newStatusesMap.get(statusId) : null;

                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "FieldStatusPermission", perm.getId(), tenant);
                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());

                    boolean hasRoles = !assignedRoles.isEmpty();

                    Long grantId = null;
                    String grantName = null;
                    List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrantsList = new ArrayList<>();

                    if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                        Grant grant = assignmentOpt.get().getGrant();
                        grantId = grant.getId();
                        grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
                    }

                    ItemTypeSet itemTypeSet = itemTypeSetId != null ? itemTypeSetRepository.findById(itemTypeSetId).orElse(null) : null;
                    if (itemTypeSet != null) {
                        if (itemTypeSet.getProject() != null) {
                            Optional<PermissionAssignment> projectAssignmentOpt =
                                    projectPermissionAssignmentService.getProjectAssignment(
                                            "FieldStatusPermission", perm.getId(),
                                            itemTypeSet.getProject().getId(), tenant);
                            projectAssignmentOpt.ifPresent(projectAssignment -> {
                                if (projectAssignment.getGrant() != null) {
                                    projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                            .projectId(itemTypeSet.getProject().getId())
                                            .projectName(itemTypeSet.getProject().getName())
                                            .build());
                                }
                            });
                        } else {
                            for (Project project : itemTypeSet.getProjectsAssociation()) {
                                Optional<PermissionAssignment> projectAssignmentOpt =
                                        projectPermissionAssignmentService.getProjectAssignment(
                                                "FieldStatusPermission", perm.getId(), project.getId(), tenant);
                                projectAssignmentOpt.ifPresent(projectAssignment -> {
                                    if (projectAssignment.getGrant() != null) {
                                        projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                                .projectId(project.getId())
                                                .projectName(project.getName())
                                                .build());
                                    }
                                });
                            }
                        }
                    }

                    boolean hasAssignments = hasRoles || grantId != null || !projectGrantsList.isEmpty();
                    boolean defaultPreserve = canPreserve && hasAssignments;
                    String suggestedAction = canPreserve ? "PRESERVE" : "REMOVE";

                    String permissionType = perm.getPermissionType() == FieldStatusPermission.PermissionType.EDITORS
                            ? "FIELD_EDITORS"
                            : "FIELD_VIEWERS";

                    return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                            .permissionId(perm.getId())
                            .permissionType(permissionType)
                            .entityId(statusId)
                            .entityName(perm.getWorkflowStatus().getStatus().getName())
                            .fieldId(fieldId)
                            .fieldName(perm.getField().getName())
                            .matchingEntityId(canPreserve && matchingStatus != null ? matchingStatus.getStatusId() : null)
                            .matchingEntityName(canPreserve && matchingStatus != null ? matchingStatus.getStatusName() : null)
                            .assignedRoles(assignedRoles)
                            .hasAssignments(hasAssignments)
                            .canBePreserved(canPreserve)
                            .defaultPreserve(defaultPreserve)
                            .suggestedAction(suggestedAction)
                            .itemTypeSetId(itemTypeSetId)
                            .itemTypeSetName(getItemTypeSetNameForConfiguration(config))
                            .projectId(itemTypeSet != null && itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet != null && itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .grantId(grantId)
                            .grantName(grantName)
                            .projectGrants(projectGrantsList)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyzeExecutorPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto.WorkflowInfo oldWorkflowInfo,
            ItemTypeConfigurationMigrationImpactDto.WorkflowInfo newWorkflowInfo,
            boolean workflowChanged
    ) {
        if (!workflowChanged) {
            return Collections.emptyList();
        }

        List<ExecutorPermission> existingPermissions = executorPermissionRepository
                .findAllByItemTypeConfiguration(config);

        Set<Long> newTransitionIds = newWorkflowInfo.getTransitions().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.TransitionInfo::getTransitionId)
                .collect(Collectors.toSet());

        Map<Long, ItemTypeConfigurationMigrationImpactDto.TransitionInfo> newTransitionsMap = newWorkflowInfo.getTransitions().stream()
                .collect(Collectors.toMap(
                        ItemTypeConfigurationMigrationImpactDto.TransitionInfo::getTransitionId,
                        java.util.function.Function.identity()
                ));

        Long itemTypeSetId = getItemTypeSetIdForConfiguration(config);
        Tenant tenant = config.getTenant();

        return existingPermissions.stream()
                .map(perm -> {
                    Transition transition = perm.getTransition();
                    Long transitionId = transition != null ? transition.getId() : null;

                    boolean canPreserve = transitionId != null && newTransitionIds.contains(transitionId);
                    ItemTypeConfigurationMigrationImpactDto.TransitionInfo matchingTransition = canPreserve
                            ? newTransitionsMap.get(transitionId)
                            : null;

                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "ExecutorPermission", perm.getId(), tenant);
                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());

                    boolean hasRoles = !assignedRoles.isEmpty();

                    Long grantId = null;
                    String grantName = null;
                    List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrantsList = new ArrayList<>();

                    if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                        Grant grant = assignmentOpt.get().getGrant();
                        grantId = grant.getId();
                        grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
                    }

                    ItemTypeSet itemTypeSet = itemTypeSetId != null ? itemTypeSetRepository.findById(itemTypeSetId).orElse(null) : null;
                    if (itemTypeSet != null) {
                        if (itemTypeSet.getProject() != null) {
                            Optional<PermissionAssignment> projectAssignmentOpt =
                                    projectPermissionAssignmentService.getProjectAssignment(
                                            "ExecutorPermission", perm.getId(),
                                            itemTypeSet.getProject().getId(), tenant);
                            projectAssignmentOpt.ifPresent(projectAssignment -> {
                                if (projectAssignment.getGrant() != null) {
                                    projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                            .projectId(itemTypeSet.getProject().getId())
                                            .projectName(itemTypeSet.getProject().getName())
                                            .build());
                                }
                            });
                        } else {
                            for (Project project : itemTypeSet.getProjectsAssociation()) {
                                Optional<PermissionAssignment> projectAssignmentOpt =
                                        projectPermissionAssignmentService.getProjectAssignment(
                                                "ExecutorPermission", perm.getId(), project.getId(), tenant);
                                projectAssignmentOpt.ifPresent(projectAssignment -> {
                                    if (projectAssignment.getGrant() != null) {
                                        projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                                .projectId(project.getId())
                                                .projectName(project.getName())
                                                .build());
                                    }
                                });
                            }
                        }
                    }

                    boolean hasAssignments = hasRoles || grantId != null || !projectGrantsList.isEmpty();
                    boolean defaultPreserve = canPreserve && hasAssignments;

                    String transitionName = transition != null ? transition.getName() : null;
                    String fromStatusName = transition != null && transition.getFromStatus() != null && transition.getFromStatus().getStatus() != null
                            ? transition.getFromStatus().getStatus().getName()
                            : null;
                    String toStatusName = transition != null && transition.getToStatus() != null && transition.getToStatus().getStatus() != null
                            ? transition.getToStatus().getStatus().getName()
                            : null;

                    return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                            .permissionId(perm.getId())
                            .permissionType("EXECUTORS")
                            .entityId(transitionId)
                            .entityName(transitionName != null ? transitionName : (fromStatusName + " -> " + toStatusName))
                            .matchingEntityId(canPreserve && matchingTransition != null ? matchingTransition.getTransitionId() : null)
                            .matchingEntityName(canPreserve && matchingTransition != null ? matchingTransition.getTransitionName() : null)
                            .assignedRoles(assignedRoles)
                            .hasAssignments(hasAssignments)
                            .canBePreserved(canPreserve)
                            .defaultPreserve(defaultPreserve)
                            .suggestedAction(canPreserve ? "PRESERVE" : "REMOVE")
                            .itemTypeSetId(itemTypeSetId)
                            .itemTypeSetName(getItemTypeSetNameForConfiguration(config))
                            .projectId(itemTypeSet != null && itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet != null && itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .grantId(grantId)
                            .grantName(grantName)
                            .projectGrants(projectGrantsList)
                            .fromStatusName(fromStatusName)
                            .toStatusName(toStatusName)
                            .transitionName(transitionName)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyzeWorkerPermissions(
            ItemTypeConfiguration config,
            ItemTypeSet itemTypeSet
    ) {
        List<WorkerPermission> existingPermissions = workerPermissionRepository.findAllByItemTypeConfiguration(config);
        if (existingPermissions.isEmpty()) {
            return Collections.emptyList();
        }

        Tenant tenant = config.getTenant();
        Long itemTypeSetId = itemTypeSet != null ? itemTypeSet.getId() : getItemTypeSetIdForConfiguration(config);
        String itemTypeSetName = itemTypeSet != null ? itemTypeSet.getName() : getItemTypeSetNameForConfiguration(config);

        return existingPermissions.stream()
                .map(perm -> {
                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "WorkerPermission", perm.getId(), tenant);
                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                                    .map(Role::getName)
                                    .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());

                    Long grantId = null;
                    String grantName = null;
                    if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                        Grant grant = assignmentOpt.get().getGrant();
                        grantId = grant.getId();
                        grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
                    }

                    ProjectAssignmentSummary projectSummary = collectProjectAssignmentSummary(
                            "WorkerPermission", perm.getId(), tenant, itemTypeSet);

                    boolean hasAssignments = !assignedRoles.isEmpty()
                            || grantId != null
                            || projectSummary.hasProjectRoles()
                            || !projectSummary.projectGrants().isEmpty();

                    return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                            .permissionId(perm.getId())
                            .permissionType("WORKERS")
                            .entityId(config.getItemType() != null ? config.getItemType().getId() : null)
                            .entityName(config.getItemType() != null ? config.getItemType().getName() : null)
                            .matchingEntityId(config.getItemType() != null ? config.getItemType().getId() : null)
                            .matchingEntityName(config.getItemType() != null ? config.getItemType().getName() : null)
                            .assignedRoles(assignedRoles)
                            .hasAssignments(hasAssignments)
                            .canBePreserved(true)
                            .defaultPreserve(hasAssignments)
                            .suggestedAction("PRESERVE")
                            .itemTypeSetId(itemTypeSetId)
                            .itemTypeSetName(itemTypeSetName)
                            .projectId(config.getProject() != null ? config.getProject().getId() : null)
                            .projectName(config.getProject() != null ? config.getProject().getName() : null)
                            .grantId(grantId)
                            .grantName(grantName)
                            .projectGrants(projectSummary.projectGrants())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyzeCreatorPermissions(
            ItemTypeConfiguration config,
            ItemTypeSet itemTypeSet
    ) {
        List<CreatorPermission> existingPermissions = creatorPermissionRepository.findAllByItemTypeConfiguration(config);
        if (existingPermissions.isEmpty()) {
            return Collections.emptyList();
        }

        Tenant tenant = config.getTenant();
        Long itemTypeSetId = itemTypeSet != null ? itemTypeSet.getId() : getItemTypeSetIdForConfiguration(config);
        String itemTypeSetName = itemTypeSet != null ? itemTypeSet.getName() : getItemTypeSetNameForConfiguration(config);

        return existingPermissions.stream()
                .map(perm -> {
                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "CreatorPermission", perm.getId(), tenant);
                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                                    .map(Role::getName)
                                    .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());

                    Long grantId = null;
                    String grantName = null;
                    if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                        Grant grant = assignmentOpt.get().getGrant();
                        grantId = grant.getId();
                        grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
                    }

                    ProjectAssignmentSummary projectSummary = collectProjectAssignmentSummary(
                            "CreatorPermission", perm.getId(), tenant, itemTypeSet);

                    boolean hasAssignments = !assignedRoles.isEmpty()
                            || grantId != null
                            || projectSummary.hasProjectRoles()
                            || !projectSummary.projectGrants().isEmpty();

                    return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                            .permissionId(perm.getId())
                            .permissionType("CREATORS")
                            .entityId(config.getItemType() != null ? config.getItemType().getId() : null)
                            .entityName(config.getItemType() != null ? config.getItemType().getName() : null)
                            .matchingEntityId(config.getItemType() != null ? config.getItemType().getId() : null)
                            .matchingEntityName(config.getItemType() != null ? config.getItemType().getName() : null)
                            .assignedRoles(assignedRoles)
                            .hasAssignments(hasAssignments)
                            .canBePreserved(true)
                            .defaultPreserve(hasAssignments)
                            .suggestedAction("PRESERVE")
                            .itemTypeSetId(itemTypeSetId)
                            .itemTypeSetName(itemTypeSetName)
                            .projectId(config.getProject() != null ? config.getProject().getId() : null)
                            .projectName(config.getProject() != null ? config.getProject().getName() : null)
                            .grantId(grantId)
                            .grantName(grantName)
                            .projectGrants(projectSummary.projectGrants())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private ProjectAssignmentSummary collectProjectAssignmentSummary(
            String permissionType,
            Long permissionId,
            Tenant tenant,
            ItemTypeSet itemTypeSet
    ) {
        List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrants = new ArrayList<>();
        boolean hasProjectRoles = false;

        if (itemTypeSet == null) {
            return new ProjectAssignmentSummary(projectGrants, hasProjectRoles);
        }

        if (itemTypeSet.getProject() != null) {
            Optional<PermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                    permissionType, permissionId, itemTypeSet.getProject().getId(), tenant);
            if (projectAssignmentOpt.isPresent()) {
                PermissionAssignment assignment = projectAssignmentOpt.get();
                if (assignment.getGrant() != null) {
                    projectGrants.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                            .projectId(itemTypeSet.getProject().getId())
                            .projectName(itemTypeSet.getProject().getName())
                            .build());
                }
                if (assignment.getRoles() != null && !assignment.getRoles().isEmpty()) {
                    hasProjectRoles = true;
                }
            }
        } else if (itemTypeSet.getProjectsAssociation() != null) {
            for (Project project : itemTypeSet.getProjectsAssociation()) {
                Optional<PermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                        permissionType, permissionId, project.getId(), tenant);
                if (projectAssignmentOpt.isPresent()) {
                    PermissionAssignment assignment = projectAssignmentOpt.get();
                    if (assignment.getGrant() != null) {
                        projectGrants.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                .projectId(project.getId())
                                .projectName(project.getName())
                                .build());
                    }
                    if (assignment.getRoles() != null && !assignment.getRoles().isEmpty()) {
                        hasProjectRoles = true;
                    }
                }
            }
        }

        return new ProjectAssignmentSummary(projectGrants, hasProjectRoles);
    }

    private record ProjectAssignmentSummary(
            List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrants,
            boolean hasProjectRoles
    ) {}

    @SafeVarargs
    private int countPreservable(
            List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact>... permissionLists
    ) {
        return (int) Arrays.stream(permissionLists)
                .flatMap(List::stream)
                .filter(ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact::isCanBePreserved)
                .count();
    }

    @SafeVarargs
    private int countRemovable(
            List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact>... permissionLists
    ) {
        return (int) Arrays.stream(permissionLists)
                .flatMap(List::stream)
                .filter(p -> !p.isCanBePreserved())
                .count();
    }

    @SafeVarargs
    private int countNew(
            List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact>... permissionLists
    ) {
        return 0;
    }

    @SafeVarargs
    private int countWithRoles(
            List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact>... permissionLists
    ) {
        return (int) Arrays.stream(permissionLists)
                .flatMap(List::stream)
                .filter(ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact::isHasAssignments)
                .count();
    }

    private String getItemTypeSetNameForConfiguration(ItemTypeConfiguration config) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByItemTypeConfigurations_IdAndTenant(config.getId(), config.getTenant())
                .stream()
                .findFirst()
                .orElse(null);
        return itemTypeSet != null ? itemTypeSet.getName() : null;
    }

    private Long getItemTypeSetIdForConfiguration(ItemTypeConfiguration config) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByItemTypeConfigurations_IdAndTenant(config.getId(), config.getTenant())
                .stream()
                .findFirst()
                .orElse(null);
        return itemTypeSet != null ? itemTypeSet.getId() : null;
    }
}

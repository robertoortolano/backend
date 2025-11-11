package com.example.demo.service;

import com.example.demo.dto.ItemTypeConfigurationMigrationImpactDto;
import com.example.demo.entity.FieldSet;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Status;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Transition;
import com.example.demo.entity.Workflow;
import com.example.demo.entity.WorkflowStatus;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ItemTypeConfigurationRepository;
import com.example.demo.repository.ItemTypeSetRepository;
import com.example.demo.repository.TransitionRepository;
import com.example.demo.repository.WorkflowStatusRepository;
import com.example.demo.service.migration.analysis.CreatorPermissionAnalysisStrategy;
import com.example.demo.service.migration.analysis.ExecutorPermissionAnalysisStrategy;
import com.example.demo.service.migration.analysis.FieldOwnerPermissionAnalysisStrategy;
import com.example.demo.service.migration.analysis.FieldStatusPermissionAnalysisStrategy;
import com.example.demo.service.migration.analysis.MigrationAnalysisContext;
import com.example.demo.service.migration.analysis.StatusOwnerPermissionAnalysisStrategy;
import com.example.demo.service.migration.analysis.WorkerPermissionAnalysisStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemTypeConfigurationMigrationAnalysisService {

    private final ItemTypeConfigurationRepository itemTypeConfigurationRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final TransitionRepository transitionRepository;
    private final FieldLookup fieldLookup;
    private final WorkflowLookup workflowLookup;
    private final FieldSetLookup fieldSetLookup;
    private final ItemTypeSetRepository itemTypeSetRepository;
    private final FieldOwnerPermissionAnalysisStrategy fieldOwnerPermissionAnalysisStrategy;
    private final StatusOwnerPermissionAnalysisStrategy statusOwnerPermissionAnalysisStrategy;
    private final FieldStatusPermissionAnalysisStrategy fieldStatusPermissionAnalysisStrategy;
    private final ExecutorPermissionAnalysisStrategy executorPermissionAnalysisStrategy;
    private final WorkerPermissionAnalysisStrategy workerPermissionAnalysisStrategy;
    private final CreatorPermissionAnalysisStrategy creatorPermissionAnalysisStrategy;

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

        Long itemTypeSetId = getItemTypeSetIdForConfiguration(oldConfig);
        ItemTypeSet owningItemTypeSet = itemTypeSetId != null
                ? itemTypeSetRepository.findById(itemTypeSetId).orElse(null)
                : null;

        String itemTypeSetName = owningItemTypeSet != null
                ? owningItemTypeSet.getName()
                : getItemTypeSetNameForConfiguration(oldConfig);

        MigrationAnalysisContext context = new MigrationAnalysisContext(
                oldConfig,
                oldFieldSet,
                newFieldSet,
                oldWorkflow,
                newWorkflow,
                oldFieldSetInfo,
                newFieldSetInfo,
                oldWorkflowInfo,
                newWorkflowInfo,
                fieldSetChanged,
                workflowChanged,
                itemTypeSetId,
                owningItemTypeSet,
                itemTypeSetName
        );

        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> fieldOwnerPermissions =
                fieldOwnerPermissionAnalysisStrategy.analyze(context);

        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> statusOwnerPermissions =
                statusOwnerPermissionAnalysisStrategy.analyze(context);

        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> fieldStatusPermissions =
                fieldStatusPermissionAnalysisStrategy.analyze(context);

        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> executorPermissions =
                executorPermissionAnalysisStrategy.analyze(context);

        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> workerPermissions =
                workerPermissionAnalysisStrategy.analyze(context);

        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> creatorPermissions =
                creatorPermissionAnalysisStrategy.analyze(context);

        int totalPreservable = countPreservable(fieldOwnerPermissions, statusOwnerPermissions, fieldStatusPermissions, executorPermissions, workerPermissions, creatorPermissions);
        int totalRemovable = countRemovable(fieldOwnerPermissions, statusOwnerPermissions, fieldStatusPermissions, executorPermissions, workerPermissions, creatorPermissions);
        int totalNew = countNew(fieldOwnerPermissions, statusOwnerPermissions, fieldStatusPermissions, executorPermissions, workerPermissions, creatorPermissions);
        int totalWithRoles = countWithRoles(fieldOwnerPermissions, statusOwnerPermissions, fieldStatusPermissions, executorPermissions, workerPermissions, creatorPermissions);

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

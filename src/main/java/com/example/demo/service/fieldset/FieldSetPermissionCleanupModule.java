package com.example.demo.service.fieldset;

import com.example.demo.entity.Field;
import com.example.demo.entity.FieldOwnerPermission;
import com.example.demo.entity.FieldSet;
import com.example.demo.entity.FieldStatusPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.WorkflowStatus;
import com.example.demo.repository.FieldOwnerPermissionRepository;
import com.example.demo.repository.FieldSetRepository;
import com.example.demo.repository.FieldStatusPermissionRepository;
import com.example.demo.service.FieldLookup;
import com.example.demo.service.ItemTypeSetLookup;
import com.example.demo.service.PermissionAssignmentService;
import com.example.demo.service.WorkflowStatusLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
class FieldSetPermissionCleanupModule {

    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final FieldSetRepository fieldSetRepository;
    private final ItemTypeSetLookup itemTypeSetLookup;
    private final FieldLookup fieldLookup;
    private final WorkflowStatusLookup workflowStatusLookup;
    private final FieldSetUpdateHelper fieldSetUpdateHelper;

    @Transactional
    public void removeOrphanedPermissions(
            Tenant tenant,
            Long fieldSetId,
            Set<Long> removedFieldConfigIds,
            Set<Long> addedFieldConfigIds,
            Set<Long> preservedPermissionIds
    ) {
        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(fieldSetId, tenant)
                .orElseThrow(() -> new com.example.demo.exception.ApiException("FieldSet not found: " + fieldSetId));

        FieldSetUpdateHelper.FieldRemovalContext removalContext = fieldSetUpdateHelper.computeRemovalContext(
                tenant,
                fieldSet,
                removedFieldConfigIds,
                addedFieldConfigIds
        );

        if (removalContext.removedFieldIds().isEmpty()) {
            return;
        }

        Set<Long> safePreserved = preservedPermissionIds != null ? preservedPermissionIds : Set.of();

        List<ItemTypeSet> affectedItemTypeSets = itemTypeSetLookup.findByFieldSetId(fieldSetId, tenant);

        removeOrphanedFieldOwnerPermissions(affectedItemTypeSets, removalContext.removedFieldIds(), safePreserved);
        removeOrphanedFieldStatusPermissions(affectedItemTypeSets, removalContext.removedFieldIds(), safePreserved);
    }

    @Transactional
    public void removeOrphanedPermissionsWithoutAssignments(
            Tenant tenant,
            Long fieldSetId,
            Set<Long> removedFieldConfigIds,
            Set<Long> addedFieldConfigIds
    ) {
        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(fieldSetId, tenant)
                .orElseThrow(() -> new com.example.demo.exception.ApiException("FieldSet not found: " + fieldSetId));

        FieldSetUpdateHelper.FieldRemovalContext removalContext = fieldSetUpdateHelper.computeRemovalContext(
                tenant,
                fieldSet,
                removedFieldConfigIds,
                addedFieldConfigIds
        );

        if (removalContext.removedFieldIds().isEmpty()) {
            return;
        }

        List<ItemTypeSet> itemTypeSets = itemTypeSetLookup.findByFieldSetId(fieldSetId, tenant);

        removeOrphanedPermissionsWithoutAssignmentsInternal(itemTypeSets, removalContext.removedFieldIds());
    }

    private void removeOrphanedFieldOwnerPermissions(
            List<ItemTypeSet> itemTypeSets,
            Set<Long> removedFieldIds,
            Set<Long> preservedPermissionIds
    ) {
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                for (Long fieldId : removedFieldIds) {
                    FieldOwnerPermission permission = fieldOwnerPermissionRepository
                            .findByItemTypeConfigurationAndFieldId(config, fieldId);

                    if (permission != null && !preservedPermissionIds.contains(permission.getId())) {
                        permissionAssignmentService.deleteAssignment(
                                "FieldOwnerPermission",
                                permission.getId(),
                                itemTypeSet.getTenant()
                        );
                        fieldOwnerPermissionRepository.delete(permission);
                    }
                }
            }
        }
    }

    private void removeOrphanedFieldStatusPermissions(
            List<ItemTypeSet> itemTypeSets,
            Set<Long> removedFieldIds,
            Set<Long> preservedPermissionIds
    ) {
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                for (Long fieldId : removedFieldIds) {
                    Field field = fieldLookup.getById(fieldId, itemTypeSet.getTenant());
                    List<WorkflowStatus> workflowStatuses = workflowStatusLookup.findAllByWorkflow(config.getWorkflow());

                    for (WorkflowStatus workflowStatus : workflowStatuses) {
                        FieldStatusPermission editorsPermission = fieldStatusPermissionRepository
                                .findByItemTypeConfigurationAndFieldAndWorkflowStatusAndPermissionType(
                                        config, field, workflowStatus, FieldStatusPermission.PermissionType.EDITORS);

                        if (editorsPermission != null && !preservedPermissionIds.contains(editorsPermission.getId())) {
                            permissionAssignmentService.deleteAssignment(
                                    "FieldStatusPermission",
                                    editorsPermission.getId(),
                                    itemTypeSet.getTenant()
                            );
                            fieldStatusPermissionRepository.delete(editorsPermission);
                        }

                        FieldStatusPermission viewersPermission = fieldStatusPermissionRepository
                                .findByItemTypeConfigurationAndFieldAndWorkflowStatusAndPermissionType(
                                        config, field, workflowStatus, FieldStatusPermission.PermissionType.VIEWERS);

                        if (viewersPermission != null && !preservedPermissionIds.contains(viewersPermission.getId())) {
                            permissionAssignmentService.deleteAssignment(
                                    "FieldStatusPermission",
                                    viewersPermission.getId(),
                                    itemTypeSet.getTenant()
                            );
                            fieldStatusPermissionRepository.delete(viewersPermission);
                        }
                    }
                }
            }
        }
    }

    private void removeOrphanedPermissionsWithoutAssignmentsInternal(
            List<ItemTypeSet> itemTypeSets,
            Set<Long> removedFieldIds
    ) {
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                for (Long fieldId : removedFieldIds) {
                    FieldOwnerPermission fieldOwnerPermission = fieldOwnerPermissionRepository
                            .findByItemTypeConfigurationAndFieldId(config, fieldId);

                    if (fieldOwnerPermission != null) {
                        fieldOwnerPermissionRepository.delete(fieldOwnerPermission);
                    }

                    Field field = fieldLookup.getById(fieldId, config.getTenant());
                    List<WorkflowStatus> workflowStatuses = workflowStatusLookup.findAllByWorkflow(config.getWorkflow());

                    for (WorkflowStatus workflowStatus : workflowStatuses) {
                        FieldStatusPermission editorsPermission = fieldStatusPermissionRepository
                                .findByItemTypeConfigurationAndFieldAndWorkflowStatusAndPermissionType(
                                        config, field, workflowStatus, FieldStatusPermission.PermissionType.EDITORS);

                        if (editorsPermission != null) {
                            fieldStatusPermissionRepository.delete(editorsPermission);
                        }

                        FieldStatusPermission viewersPermission = fieldStatusPermissionRepository
                                .findByItemTypeConfigurationAndFieldAndWorkflowStatusAndPermissionType(
                                        config, field, workflowStatus, FieldStatusPermission.PermissionType.VIEWERS);

                        if (viewersPermission != null) {
                            fieldStatusPermissionRepository.delete(viewersPermission);
                        }
                    }
                }
            }
        }
    }
}


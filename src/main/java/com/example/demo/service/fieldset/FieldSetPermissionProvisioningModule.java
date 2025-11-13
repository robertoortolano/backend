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
class FieldSetPermissionProvisioningModule {

    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ItemTypeSetLookup itemTypeSetLookup;
    private final FieldLookup fieldLookup;
    private final WorkflowStatusLookup workflowStatusLookup;

    @Transactional
    public void handlePermissionsForNewFields(Tenant tenant, FieldSet fieldSet, Set<Long> newFieldIds) {
        if (newFieldIds == null || newFieldIds.isEmpty()) {
            return;
        }

        List<ItemTypeSet> affectedItemTypeSets = itemTypeSetLookup.findByFieldSetId(fieldSet.getId(), tenant);

        for (ItemTypeSet itemTypeSet : affectedItemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                if (config.getFieldSet().getId().equals(fieldSet.getId())) {
                    createPermissionsForNewFields(config, newFieldIds);
                }
            }
        }
    }

    private void createPermissionsForNewFields(ItemTypeConfiguration config, Set<Long> newFieldIds) {
        for (Long fieldId : newFieldIds) {
            createFieldOwnerPermission(config, fieldId);
            createFieldStatusPermissions(config, fieldId);
        }
    }

    private void createFieldOwnerPermission(ItemTypeConfiguration config, Long fieldId) {
        FieldOwnerPermission existingPermission = fieldOwnerPermissionRepository
                .findByItemTypeConfigurationAndFieldId(config, fieldId);

        if (existingPermission == null) {
            Field field = fieldLookup.getById(fieldId, config.getTenant());

            FieldOwnerPermission permission = new FieldOwnerPermission();
            permission.setItemTypeConfiguration(config);
            permission.setField(field);
            fieldOwnerPermissionRepository.save(permission);
        } else {
            permissionAssignmentService.deleteAssignment(
                    "FieldOwnerPermission",
                    existingPermission.getId(),
                    config.getTenant()
            );
        }
    }

    private void createFieldStatusPermissions(ItemTypeConfiguration config, Long fieldId) {
        Field field = fieldLookup.getById(fieldId, config.getTenant());
        List<WorkflowStatus> workflowStatuses = workflowStatusLookup.findAllByWorkflow(config.getWorkflow());

        for (WorkflowStatus workflowStatus : workflowStatuses) {
            createFieldStatusPermission(config, field, workflowStatus, FieldStatusPermission.PermissionType.EDITORS);
            createFieldStatusPermission(config, field, workflowStatus, FieldStatusPermission.PermissionType.VIEWERS);
        }
    }

    private void createFieldStatusPermission(
            ItemTypeConfiguration config,
            Field field,
            WorkflowStatus workflowStatus,
            FieldStatusPermission.PermissionType permissionType
    ) {
        FieldStatusPermission existingPermission = fieldStatusPermissionRepository
                .findByItemTypeConfigurationAndFieldAndWorkflowStatusAndPermissionType(
                        config, field, workflowStatus, permissionType);

        if (existingPermission == null) {
            FieldStatusPermission permission = new FieldStatusPermission();
            permission.setItemTypeConfiguration(config);
            permission.setField(field);
            permission.setWorkflowStatus(workflowStatus);
            permission.setPermissionType(permissionType);
            fieldStatusPermissionRepository.save(permission);
        } else {
            permissionAssignmentService.deleteAssignment(
                    "FieldStatusPermission",
                    existingPermission.getId(),
                    config.getTenant()
            );
        }
    }
}






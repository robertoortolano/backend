package com.example.demo.service.fieldset;

import com.example.demo.dto.FieldSetRemovalImpactDto;
import com.example.demo.entity.FieldSet;
import com.example.demo.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class FieldSetPermissionManager {

    private final FieldSetPermissionProvisioningModule provisioningModule;
    private final FieldSetPermissionReportingModule reportingModule;
    private final FieldSetPermissionCleanupModule cleanupModule;

    public void handlePermissionsForNewFields(Tenant tenant, FieldSet fieldSet, Set<Long> newFieldIds) {
        provisioningModule.handlePermissionsForNewFields(tenant, fieldSet, newFieldIds);
    }

    public FieldSetRemovalImpactDto analyzeRemovalImpact(
            Tenant tenant,
            Long fieldSetId,
            Set<Long> removedFieldConfigIds,
            Set<Long> addedFieldConfigIds
    ) {
        return reportingModule.analyzeRemovalImpact(tenant, fieldSetId, removedFieldConfigIds, addedFieldConfigIds);
    }

    public boolean hasAssignments(FieldSetRemovalImpactDto impact) {
        return reportingModule.hasAssignments(impact);
    }

    public void removeOrphanedPermissions(
            Tenant tenant,
            Long fieldSetId,
            Set<Long> removedFieldConfigIds,
            Set<Long> addedFieldConfigIds,
            Set<Long> preservedPermissionIds
    ) {
        cleanupModule.removeOrphanedPermissions(
                tenant,
                fieldSetId,
                removedFieldConfigIds,
                addedFieldConfigIds,
                preservedPermissionIds
        );
    }

    public void removeOrphanedPermissionsWithoutAssignments(
            Tenant tenant,
            Long fieldSetId,
            Set<Long> removedFieldConfigIds,
            Set<Long> addedFieldConfigIds
    ) {
        cleanupModule.removeOrphanedPermissionsWithoutAssignments(
                tenant,
                fieldSetId,
                removedFieldConfigIds,
                addedFieldConfigIds
        );
    }
}


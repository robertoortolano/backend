package com.example.demo.service.itemtypeset;

import com.example.demo.dto.ItemTypeConfigurationRemovalImpactDto;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.Tenant;
import com.example.demo.service.permission.itemtypeset.ItemTypeSetPermissionProvisioningModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class ItemTypeSetPermissionOrchestrator {

    private final ItemTypeSetPermissionImpactModule impactModule;
    private final ItemTypeSetPermissionCleanupModule cleanupModule;
    private final ItemTypeSetPermissionProvisioningModule provisioningModule;

    public ItemTypeConfigurationRemovalImpactDto analyzeRemovalImpact(
            Tenant tenant,
            Long itemTypeSetId,
            Set<Long> removedItemTypeConfigurationIds
    ) {
        return impactModule.analyzeRemovalImpact(tenant, itemTypeSetId, removedItemTypeConfigurationIds);
    }

    public boolean hasAssignments(ItemTypeConfigurationRemovalImpactDto impact) {
        return impactModule.hasAssignments(impact);
    }

    public void removeOrphanedPermissionsForItemTypeConfigurations(
            Tenant tenant,
            Long itemTypeSetId,
            Set<Long> removedItemTypeConfigurationIds,
            Set<Long> preservedPermissionIds
    ) {
        cleanupModule.removeOrphanedPermissionsForItemTypeConfigurations(
                tenant,
                itemTypeSetId,
                removedItemTypeConfigurationIds,
                preservedPermissionIds
        );
    }

    public void handlePermissionsForNewConfiguration(ItemTypeConfiguration configuration) {
        provisioningModule.createPermissionsForConfiguration(configuration);
    }

    public void ensureItemTypeSetPermissions(Long itemTypeSetId, Tenant tenant) {
        provisioningModule.createPermissionsForItemTypeSet(itemTypeSetId, tenant);
    }
}


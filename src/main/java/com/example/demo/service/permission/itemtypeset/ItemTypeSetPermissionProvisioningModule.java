package com.example.demo.service.permission.itemtypeset;

import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Tenant;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ItemTypeSetRepository;
import com.example.demo.service.ItemTypePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ItemTypeSetPermissionProvisioningModule {

    private final ItemTypeSetRepository itemTypeSetRepository;
    private final ItemTypePermissionService itemTypePermissionService;

    public void createPermissionsForItemTypeSet(Long itemTypeSetId, Tenant tenant) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findById(itemTypeSetId)
                .orElseThrow(() -> new ApiException("ItemTypeSet not found"));

        for (ItemTypeConfiguration configuration : itemTypeSet.getItemTypeConfigurations()) {
            itemTypePermissionService.createPermissionsForItemTypeConfiguration(configuration);
        }
    }

    public void createPermissionsForConfiguration(ItemTypeConfiguration configuration) {
        itemTypePermissionService.createPermissionsForItemTypeConfiguration(configuration);
    }
}


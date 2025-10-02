package com.example.demo.service;

import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.Project;
import com.example.demo.entity.Tenant;
import com.example.demo.repository.ItemTypeConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class ItemTypeConfigurationService {

    private final ItemTypeConfigurationRepository itemTypeConfigurationRepository;

    private final ItemTypeSetLookup itemTypeSetLookup;

    public boolean isItemTypeInAnyItemTypeConfiguration(Tenant tenant, Long itemTypeId) {
        return itemTypeConfigurationRepository
                .existsByItemTypeIdAndProjectTenantId(itemTypeId, tenant.getId());
    }

    @Transactional(readOnly = true)
    public Set<Project> getProjectsUsingItemType(Long itemTypeId, Tenant tenant) {
        return itemTypeConfigurationRepository.findProjectsUsingItemType(itemTypeId, tenant.getId());
    }

    @Transactional(readOnly = true)
    public ItemTypeConfiguration getConfigurationById(Long itemTypeConfigurationId, Tenant tenant) {
        return itemTypeConfigurationRepository.findByIdAndTenant(itemTypeConfigurationId, tenant);
    }

    @Transactional(readOnly = true)
    public boolean isItemTypeConfigurationInAnyItemTypeSet(Long itemTypeConfigurationId, Tenant tenant) {
        return itemTypeSetLookup.isItemTypeConfigurationInAnyItemTypeSet(itemTypeConfigurationId, tenant);
    }

    @Transactional(readOnly = true)
    public boolean isItemTypeConfigurationInAnyFieldSet(Long fieldSetId, Tenant tenant) {
        return !itemTypeConfigurationRepository.existsByFieldSetIdAndFieldSetTenantId(fieldSetId, tenant.getId());
    }
}

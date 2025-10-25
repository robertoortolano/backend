package com.example.demo.service;

import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.Tenant;
import com.example.demo.repository.ItemTypeConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemTypeConfigurationLookup {

    private final ItemTypeConfigurationRepository itemTypeConfigurationRepository;

    public boolean isItemTypeConfigurationInAnyFieldSet(Long fieldSetId, Tenant tenant) {
        return itemTypeConfigurationRepository.existsByFieldSetIdAndFieldSetTenantId(fieldSetId, tenant.getId());
    }

    public boolean isWorkflowNotInAnyItemTypeSet(Long workflowId, Tenant tenant) {
        return !itemTypeConfigurationRepository.existsByWorkflowIdAndWorkflowTenantId(workflowId, tenant.getId());
    }

    public List<ItemTypeConfiguration> getAllByItemType(Long itemTypeId, Tenant tenant) {
        return itemTypeConfigurationRepository.findByItemTypeIdAndTenant(itemTypeId, tenant);
    }

    public List<ItemTypeConfiguration> getAllByWorkflow(Long workflowId, Tenant tenant) {
        return itemTypeConfigurationRepository.findByWorkflowIdAndTenant(workflowId, tenant.getId());
    }
}

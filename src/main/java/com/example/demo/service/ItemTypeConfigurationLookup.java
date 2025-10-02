package com.example.demo.service;

import com.example.demo.entity.Tenant;
import com.example.demo.repository.ItemTypeConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemTypeConfigurationLookup {

    private final ItemTypeConfigurationRepository itemTypeConfigurationRepository;

    public boolean isItemTypeConfigurationInAnyFieldSet(Long fieldSetId, Tenant tenant) {
        return !itemTypeConfigurationRepository.existsByFieldSetIdAndFieldSetTenantId(fieldSetId, tenant.getId());
    }

    public boolean isWorkflowNotInAnyItemTypeSet(Long workflowId, Tenant tenant) {
        return !itemTypeConfigurationRepository.existsByWorkflowIdAndWorkflowTenantId(workflowId, tenant.getId());
    }
}

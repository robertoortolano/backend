package com.example.demo.service;

import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Tenant;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ItemTypeSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemTypeSetLookup {

    private final ItemTypeSetRepository itemTypeSetRepository;

    private static final String ITEMTYPESET_NOT_FOUND = "ItemTypeSet not found";


    public boolean isItemTypeConfigurationInAnyItemTypeSet(Long itemTypeConfigurationId, Tenant tenant) {
        return itemTypeSetRepository.existsByItemTypeConfigurations_IdAndTenant_Id(itemTypeConfigurationId, tenant.getId());
    }


    public ItemTypeSet getById(Tenant tenant, Long id) {
        return itemTypeSetRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException(ITEMTYPESET_NOT_FOUND));
    }

    /**
     * Trova tutti gli ItemTypeSet che usano un FieldSet specifico
     */
    public List<ItemTypeSet> findByFieldSetId(Long fieldSetId, Tenant tenant) {
        return itemTypeSetRepository.findByItemTypeConfigurationsFieldSetIdAndTenant(fieldSetId, tenant);
    }

    /**
     * Trova tutti gli ItemTypeSet che usano un Workflow specifico
     */
    public List<ItemTypeSet> findByWorkflowId(Long workflowId, Tenant tenant) {
        return itemTypeSetRepository.findByItemTypeConfigurationsWorkflowIdAndTenant(workflowId, tenant);
    }

    /**
     * Trova tutti gli ItemTypeSet che contengono una specifica ItemTypeConfiguration, filtrato per Tenant (sicurezza)
     */
    public List<ItemTypeSet> findByItemTypeConfigurationId(Long itemTypeConfigurationId, Tenant tenant) {
        return itemTypeSetRepository.findByItemTypeConfigurations_IdAndTenant(itemTypeConfigurationId, tenant);
    }

}

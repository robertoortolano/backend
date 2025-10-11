package com.example.demo.service;

import com.example.demo.dto.ItemTypeConfigurationCreateDto;
import com.example.demo.dto.ItemTypeSetCreateDto;
import com.example.demo.dto.ItemTypeSetUpdateDto;
import com.example.demo.dto.ItemTypeSetViewDto;
import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.factory.FieldSetCloner;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ItemTypeSetService {

    private final ItemTypeSetRepository itemTypeSetRepository;

    private final FieldSetRepository fieldSetRepository;
    private final ItemTypeConfigurationRepository itemTypeConfigurationRepository;

    private final FieldSetLookup fieldSetLookup;
    private final ItemTypeLookup itemTypeLookup;
    private final ProjectLookup projectLookup;
    private final ItemTypeSetLookup itemTypeSetLookup;
    private final WorkflowLookup workflowLookup;

    private final DtoMapperFacade dtoMapper;

    private final FieldSetCloner fieldSetCloner;
    private final ItemTypePermissionService itemTypePermissionService;
    private final ItemTypeSetPermissionService itemTypeSetPermissionService;
    
    private static final String ITEMTYPESET_NOT_FOUND = "ItemTypeSet not found";


    private ItemTypeSetViewDto createItemTypeSet(
            Tenant tenant,
            ItemTypeSetCreateDto dto,
            ScopeType scopeType,
            Project project
    ) {

        if (dto.itemTypeConfigurations().isEmpty())
            throw new ApiException("There must be at least an item type");

        ItemTypeSet set = new ItemTypeSet();
        set.setScope(scopeType);
        set.setTenant(tenant);
        set.setName(dto.name());

        if (!scopeType.equals(ScopeType.TENANT)) {
            if (project == null) throw new ApiException("Project must be specified for non-global ItemTypeSet");
            set.getProjectsAssociation().add(project);
        }

        // Recupera il FieldSet di default del tenant
        FieldSet defaultFieldSet = fieldSetLookup.getFirstDefault(tenant);
        if (defaultFieldSet == null) {
            throw new ApiException("No default FieldSet found for tenant ID " + tenant.getId());
        }

        Set<ItemTypeConfiguration> configurations = new HashSet<>();
        for (ItemTypeConfigurationCreateDto entryDto : dto.itemTypeConfigurations()) {
            ItemType itemType = itemTypeLookup.getById(tenant, entryDto.itemTypeId());
            Workflow workflow = workflowLookup.getByIdEntity(tenant, entryDto.workflowId());
            FieldSet fieldSet = fieldSetLookup.getById(entryDto.fieldSetId(), tenant);

            ItemTypeConfiguration configuration = new ItemTypeConfiguration();
            configuration.setTenant(tenant);
            configuration.setScope(scopeType);
            configuration.setItemType(itemType);
            configuration.setCategory(entryDto.category());
            configuration.setWorkflow(workflow);
            configuration.setFieldSet(fieldSet);

            /*
            if (!scopeType.equals(ScopeType.TENANT)) {
                // Clona il FieldSet di default per ogni entry con il FieldSetCloner aggiornato
                FieldSet clonedFieldSet = fieldSetCloner.cloneFieldSet(defaultFieldSet, " (copy for " + itemType.getName() + ")");
                fieldSetRepository.save(clonedFieldSet);

                configuration.setFieldSet(clonedFieldSet);
            }
             */

            itemTypeConfigurationRepository.save(configuration);
            
            // Crea le permissions base per questa configurazione (Creator, Executor, ecc.)
            itemTypePermissionService.createPermissionsForItemTypeConfiguration(configuration);
            
            configurations.add(configuration);
        }

        set.setItemTypeConfigurations(configurations);

        ItemTypeSet saved = itemTypeSetRepository.save(set);
        
        // Crea tutte le permissions per l'ItemTypeSet (WORKER, STATUS_OWNER, FIELD_EDITOR, ecc.)
        itemTypeSetPermissionService.createPermissionsForItemTypeSet(saved.getId(), tenant);
        
        return dtoMapper.toItemTypeSetViewDto(saved);
    }


    public ItemTypeSetViewDto createGlobal(Tenant tenant, ItemTypeSetCreateDto dto) {
        return this.createItemTypeSet(tenant, dto, ScopeType.TENANT, null);
    }


    public ItemTypeSetViewDto createForProject(Tenant tenant, Long projectId, ItemTypeSetCreateDto dto) {
        // Carico il progetto per tenant e id
        Project project = projectLookup.getById(tenant, projectId);

        ItemTypeSetViewDto itemTypeSet = this.createItemTypeSet(tenant, dto, ScopeType.PROJECT, project);
        return itemTypeSet;
    }



    public ItemTypeSetViewDto updateItemTypeSet(Tenant tenant, Long id, ItemTypeSetUpdateDto dto) {

        ItemTypeSet set = itemTypeSetRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException("ItemTypeSet non trovato: " + id));

        if (set.isDefaultItemTypeSet()) {
            throw new ApiException("Default Item Type Set cannot be edited");
        }

        Set<ItemTypeConfiguration> newConfigurations = new HashSet<>();

        // Recupera il FieldSet di default del tenant (per clonazioni)
        FieldSet defaultFieldSet = fieldSetLookup.getFirstDefault(tenant);

        for (ItemTypeConfigurationCreateDto entryDto : dto.itemTypeConfigurations()) {
            ItemType itemType = itemTypeLookup.getById(tenant, entryDto.itemTypeId());
            Workflow workflow = workflowLookup.getByIdEntity(tenant, entryDto.workflowId());
            FieldSet fieldSet = fieldSetLookup.getById(entryDto.fieldSetId(), tenant);

            ItemTypeConfiguration entry = new ItemTypeConfiguration();
            entry.setTenant(tenant);
            entry.setScope(set.getScope());
            entry.setItemType(itemType);
            entry.setCategory(entryDto.category());
            entry.setWorkflow(workflow);

            if (!set.getScope().equals(ScopeType.TENANT)) {
                // Clona il FieldSet associato
                FieldSet clonedFieldSet = fieldSetCloner.cloneFieldSet(defaultFieldSet, " (copy for " + itemType.getName() + ")");
                fieldSetRepository.save(clonedFieldSet);
                entry.setFieldSet(clonedFieldSet);
            } else {
                entry.setFieldSet(fieldSet);
            }

            // salva subito la configuration
            itemTypeConfigurationRepository.save(entry);

            newConfigurations.add(entry);
        }

        set.getItemTypeConfigurations().clear();
        set.getItemTypeConfigurations().addAll(newConfigurations);

        ItemTypeSet updated = itemTypeSetRepository.save(set);
        return dtoMapper.toItemTypeSetViewDto(updated);
    }





    @Transactional(readOnly = true)
    public ItemTypeSetViewDto getById(Tenant tenant, Long id) {
        return dtoMapper.toItemTypeSetViewDto(itemTypeSetLookup.getById(tenant, id));
    }


    @Transactional(readOnly = true)
    public List<ItemTypeSetViewDto> getAllGlobalItemTypeSets(Tenant tenant) {

        List<ItemTypeSet> sets = itemTypeSetRepository.findAllGlobalWithItemTypeConfigurationsByTenant(tenant);

        return sets.stream()
                .map(dtoMapper::toItemTypeSetViewDto)
                .toList();
    }


    @Transactional(readOnly = true)
    public List<ItemTypeSetViewDto> getAllProjectItemTypeSets(Tenant tenant) {

        List<ItemTypeSet> sets = itemTypeSetRepository.findAllNonGlobalWithItemTypeConfigurationsByTenant(tenant);

        return sets.stream()
                .map(dtoMapper::toItemTypeSetViewDto)
                .toList();
    }


    public void deleteItemTypeSet(Tenant tenant, Long id) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException(ITEMTYPESET_NOT_FOUND));

        if (itemTypeSet.isDefaultItemTypeSet()) {
            throw new ApiException("Default Item Type Set cannot be deleted");
        }

        itemTypeSetRepository.deleteByIdAndTenant(id, tenant);
    }

}

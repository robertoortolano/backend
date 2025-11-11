package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.*;
import com.example.demo.service.fieldset.FieldSetCloneHelper;
import com.example.demo.service.fieldset.FieldSetPermissionManager;
import com.example.demo.service.fieldset.FieldSetUpdateHelper;
import com.example.demo.service.fieldset.FieldSetUpdateHelper.FieldSetUpdateContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FieldSetService {

    private final FieldSetRepository fieldSetRepository;
    private final FieldSetEntryRepository fieldSetEntryRepository;
    private final ProjectRepository projectRepository;
    private final FieldConfigurationLookup fieldConfigurationLookup;
    private final ItemTypeConfigurationLookup itemTypeConfigurationLookup;
    private final FieldSetLookup fieldSetLookup;
    private final DtoMapperFacade dtoMapper;
    private final FieldSetCloneHelper fieldSetCloneHelper;
    private final FieldSetUpdateHelper fieldSetUpdateHelper;
    private final FieldSetPermissionManager fieldSetPermissionManager;

    private static final String FIELDSET_NOT_FOUND = "FieldSet not found";

    public FieldSetViewDto createGlobalFieldSet(FieldSetCreateDto dto, Tenant tenant) {
        FieldSet fieldSet = fieldSetCloneHelper.createFieldSet(dto, tenant, ScopeType.TENANT, null);
        return dtoMapper.toFieldSetViewDto(fieldSet);
    }

    public FieldSetViewDto updateFieldSet(Tenant tenant, Long id, FieldSetCreateDto dto) {
        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException(FIELDSET_NOT_FOUND + ": " + id));

        if (fieldSet.isDefaultFieldSet()) throw new ApiException("Default Field Set cannot be edited");

        FieldSetUpdateContext context = fieldSetUpdateHelper.buildUpdateContext(tenant, fieldSet, dto);

        if (!context.removedConfigIds().isEmpty()) {
            FieldSetRemovalImpactDto impact = fieldSetPermissionManager.analyzeRemovalImpact(
                    tenant,
                    id,
                    context.removedConfigIds(),
                    context.addedConfigIds()
            );

            if (fieldSetPermissionManager.hasAssignments(impact)) {
                throw new ApiException("FIELDSET_REMOVAL_IMPACT: detected permissions with assignments for removed fields");
            }

            fieldSetPermissionManager.removeOrphanedPermissionsWithoutAssignments(
                    tenant,
                    id,
                    context.removedConfigIds(),
                    context.addedConfigIds()
            );
        }

        fieldSet.setName(dto.name());
        fieldSet.setDescription(dto.description());

        fieldSetUpdateHelper.applyFieldSetEntries(tenant, fieldSet, context.entryDtos());

        FieldSet saved = fieldSetRepository.save(fieldSet);
        
        if (!context.trulyNewFieldIds().isEmpty()) {
            fieldSetPermissionManager.handlePermissionsForNewFields(tenant, saved, context.trulyNewFieldIds());
        }
        
        return dtoMapper.toFieldSetViewDto(saved);
    }


    @Transactional(readOnly = true)
    public List<FieldSetViewDto> getGlobalFieldSets(Tenant tenant) {

        List<FieldSet> sets = fieldSetRepository.findByTenantAndScope(tenant, ScopeType.TENANT);
        return sets.stream().map(dtoMapper::toFieldSetViewDto).toList();
    }


    @Transactional(readOnly = true)
    public FieldSetViewDto getById(Tenant tenant, Long id) {
        return dtoMapper.toFieldSetViewDto(fieldSetLookup.getById(id, tenant));
    }

    public void delete(Tenant tenant, Long id) {

        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException("Field Set not found"));

        if (fieldSet.isDefaultFieldSet()) throw new ApiException("Default Field Set cannot be deleted");
        
        if (!isNotInAnyItemTypeSet(id, tenant)) {
            throw new ApiException("Field Set is used in an ItemTypeSet and cannot be deleted");
        }

        fieldSetRepository.deleteByIdAndTenant(id, tenant);
    }

    @Transactional
    public void reorderEntries(Tenant tenant, Long fieldSetId, List<EntryOrderDto> newOrder) {
        // Recupera il FieldSet con le entries
        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(fieldSetId, tenant)
                .orElseThrow(() -> new ApiException("FieldSet non trovato"));

        if (fieldSet.isDefaultFieldSet()) throw new ApiException("Default Field Set cannot be edited");

        Map<Long, FieldSetEntry> entryMap = fieldSet.getFieldSetEntries()
                .stream()
                .collect(Collectors.toMap(FieldSetEntry::getId, e -> e));

        for (EntryOrderDto dto : newOrder) {
            FieldSetEntry entry = entryMap.get(dto.entryId());
            if (entry == null) {
                throw new ApiException("Entry non trovata: id=" + dto.entryId());
            }
            entry.setOrderIndex(dto.orderIndex());
        }

        // Persisti il cambiamento. Se il repository è un CrudRepository, non serve il save esplicito.
    }

    @Transactional
    public void deleteEntry(Tenant tenant, Long entryId) {
        FieldSetEntry entry = fieldSetEntryRepository.findById(entryId)
                .orElseThrow(() -> new ApiException("FieldSetEntry non trovata: " + entryId));

        if (entry.getFieldSet().isDefaultFieldSet()) throw new ApiException("Default Field Set cannot be edited");

        if (entry.getFieldSet().getTenant().equals(tenant)) {
            // Rimuovi solo l'entry (non toccare la FieldConfiguration)
            fieldSetEntryRepository.delete(entry);
        }
    }


    public FieldSetEntryViewDto addEntry(Tenant tenant, Long fieldSetId, FieldSetEntryCreateDto dto) {
        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(fieldSetId, tenant)
                .orElseThrow(() -> new ApiException("FieldSet non trovato"));

        if (fieldSet.isDefaultFieldSet()) throw new ApiException("Default Field Set cannot be edited");

        FieldConfiguration config = fieldConfigurationLookup.getById(dto.fieldConfigurationId(), tenant);

        // Verifica se è già presente
        boolean alreadyPresent = fieldSet.getFieldSetEntries().stream()
                .anyMatch(e -> e.getFieldConfiguration().getId().equals(config.getId()));

        if (alreadyPresent) {
            throw new ApiException("Questa FieldConfiguration è già presente nel FieldSet");
        }

        FieldSetEntry entry = new FieldSetEntry();
        entry.setFieldSet(fieldSet);
        entry.setFieldConfiguration(config);
        entry.setOrderIndex(dto.orderIndex());

        FieldSetEntry saved = fieldSetEntryRepository.save(entry);

        return dtoMapper.toFieldSetEntryViewDto(saved);
    }


    public boolean isNotInAnyItemTypeSet(Long fieldSetId, Tenant tenant) {
        return !itemTypeConfigurationLookup.isItemTypeConfigurationInAnyFieldSet(fieldSetId, tenant);
    }

    public FieldSet getByFieldSetEntryId(Tenant tenant, Long fieldSetEntryId) {
        FieldSetEntry fieldSetEntry = fieldSetEntryRepository.findById(fieldSetEntryId)
                .orElseThrow(() -> new ApiException("Field Set Entry not found"));
        if (!fieldSetEntry.getFieldSet().getTenant().equals(tenant))
            throw new ApiException("Illegal tenant");
        return fieldSetEntry.getFieldSet();
    }

    public FieldSetViewDto createProjectFieldSet(Tenant tenant, Long projectId, FieldSetCreateDto dto) {
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        FieldSet fieldSet = fieldSetCloneHelper.createFieldSet(dto, tenant, ScopeType.PROJECT, project);
        return dtoMapper.toFieldSetViewDto(fieldSet);
    }

    @Transactional(readOnly = true)
    public List<FieldSetViewDto> getProjectFieldSets(Tenant tenant, Long projectId) {
        List<FieldSet> fieldSets = fieldSetRepository.findByTenantAndProjectIdAndScope(tenant, projectId, ScopeType.PROJECT);
        return dtoMapper.toFieldSetViewDtos(fieldSets);
    }

    @Transactional(readOnly = true)
    public FieldSetRemovalImpactDto analyzeFieldSetRemovalImpact(
            Tenant tenant, 
            Long fieldSetId, 
            Set<Long> removedFieldConfigIds,
            Set<Long> addedFieldConfigIds
    ) {
        return fieldSetPermissionManager.analyzeRemovalImpact(tenant, fieldSetId, removedFieldConfigIds, addedFieldConfigIds);
    }

    @Transactional
    public void removeOrphanedPermissions(
            Tenant tenant, 
            Long fieldSetId, 
            Set<Long> removedFieldConfigIds,
            Set<Long> addedFieldConfigIds,
            Set<Long> preservedPermissionIds
    ) {
        fieldSetPermissionManager.removeOrphanedPermissions(
                tenant,
                fieldSetId,
                removedFieldConfigIds,
                addedFieldConfigIds,
                preservedPermissionIds
        );
    }

    @Transactional
    public void removeOrphanedPermissionsWithoutAssignments(
            Tenant tenant,
            Long fieldSetId,
            Set<Long> removedFieldConfigIds,
            Set<Long> addedFieldConfigIds
    ) {
        fieldSetPermissionManager.removeOrphanedPermissionsWithoutAssignments(
                tenant,
                fieldSetId,
                removedFieldConfigIds,
                addedFieldConfigIds
        );
    }

}

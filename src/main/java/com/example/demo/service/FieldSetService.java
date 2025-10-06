package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FieldSetService {

    private final FieldSetRepository fieldSetRepository;
    private final FieldSetEntryRepository fieldSetEntryRepository;

    private final FieldConfigurationLookup fieldConfigurationLookup;
    private final ItemTypeConfigurationLookup itemTypeConfigurationLookup;
    private final FieldSetLookup fieldSetLookup;

    private final DtoMapperFacade dtoMapper;

    private static final String FIELDSET_NOT_FOUND = "FieldSet not found";

    private FieldSet createFieldSet(FieldSetCreateDto dto, Tenant tenant, ScopeType scopeType, Project project) {
        List<Long>  configIds = dto.entries().stream()
                .map(FieldSetEntryCreateDto::fieldConfigurationId)
                .toList();

        // Recupera le FieldConfiguration dal DB
        List<FieldConfiguration> configurations = fieldConfigurationLookup.getAll(configIds, tenant);

        // Mappa ID → FieldConfiguration per accesso veloce
        Map<Long, FieldConfiguration> configMap = configurations.stream()
                .collect(Collectors.toMap(FieldConfiguration::getId, fc -> fc));

        // Crea FieldSet
        FieldSet fieldSet = new FieldSet();
        fieldSet.setName(dto.name());
        fieldSet.setDescription(dto.description());
        fieldSet.setScope(scopeType);
        fieldSet.setTenant(tenant);
        fieldSet.setDefaultFieldSet(false);

        List<FieldSetEntry> entries = new ArrayList<>();

        for (FieldSetEntryCreateDto entryDto : dto.entries()) {
            FieldConfiguration config = configMap.get(entryDto.fieldConfigurationId());
            if (config == null) {
                throw new ApiException("Configurazione non trovata: ID " + entryDto.fieldConfigurationId());
            }

            FieldSetEntry entry = new FieldSetEntry();
            entry.setFieldSet(fieldSet);
            entry.setFieldConfiguration(config);
            entry.setOrderIndex(entryDto.orderIndex());

            entries.add(entry);
        }

        fieldSet.setFieldSetEntries(entries);

        return fieldSetRepository.save(fieldSet);
    }

    private void applyFieldSetEntries(Tenant tenant, FieldSet fieldSet, List<FieldSetEntryCreateDto> entryDtos) {
        List<FieldSetEntry> entries = fieldSet.getFieldSetEntries();

        // Rimuovi le entries non più presenti
        entries.removeIf(entry -> entryDtos.stream()
                .noneMatch(dto -> dto.fieldConfigurationId().equals(entry.getFieldConfiguration().getId())));

        // Aggiungi nuove o aggiorna esistenti
        for (int i = 0; i < entryDtos.size(); i++) {
            FieldSetEntryCreateDto dto = entryDtos.get(i);

            Optional<FieldSetEntry> existingEntryOpt = entries.stream()
                    .filter(e -> e.getFieldConfiguration().getId().equals(dto.fieldConfigurationId()))
                    .findFirst();

            if (existingEntryOpt.isPresent()) {
                // Aggiorna orderIndex esistente
                FieldSetEntry existingEntry = existingEntryOpt.get();
                existingEntry.setOrderIndex(i);
            } else {
                // Nuova entry
                FieldConfiguration fc = fieldConfigurationLookup.getById(dto.fieldConfigurationId(), tenant);

                FieldSetEntry newEntry = new FieldSetEntry();
                newEntry.setFieldSet(fieldSet);
                newEntry.setFieldConfiguration(fc);
                newEntry.setOrderIndex(i);

                entries.add(newEntry);
            }
        }
    }



    public FieldSetViewDto createGlobalFieldSet(FieldSetCreateDto dto, Tenant tenant) {
        FieldSet fieldSet = createFieldSet(dto, tenant, ScopeType.TENANT, null);
        return dtoMapper.toFieldSetViewDto(fieldSet);
    }

    public FieldSetViewDto updateFieldSet(Tenant tenant, Long id, FieldSetCreateDto dto) {
        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException(FIELDSET_NOT_FOUND + ": " + id));

        if (fieldSet.isDefaultFieldSet()) throw new ApiException("Default Field Set cannot be edited");

        fieldSet.setName(dto.name());
        fieldSet.setDescription(dto.description());

        // ✅ Sostituisce tutta la logica sotto con un metodo chiaro e sicuro
        applyFieldSetEntries(tenant, fieldSet, dto.entries());

        FieldSet saved = fieldSetRepository.save(fieldSet);
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
            throw new IllegalStateException("Questa FieldConfiguration è già presente nel FieldSet");
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

/*
    public FieldSetViewDto createProjectFieldSet(Tenant tenant, Long projectId, FieldSetCreateDto dto) {
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        return createFieldSet(dto, false, project);
    }















    public FieldSet addConfigurationToFieldSet(Long fieldSetId, Long configurationId, int orderIndex) {
        FieldSet fieldSet = fieldSetRepository.findById(fieldSetId)
                .orElseThrow(() -> new ApiException(FIELDSET_NOT_FOUND));
        FieldConfiguration configuration = fieldConfigurationRepository.findById(configurationId)
                .orElseThrow(() -> new ApiException("FieldConfiguration non trovata"));

        fieldSet.addConfiguration(configuration, orderIndex);
        return fieldSetRepository.save(fieldSet);
    }

    public FieldSet removeConfigurationFromFieldSet(Long fieldSetId, Long configurationId) {
        FieldSet fieldSet = fieldSetRepository.findById(fieldSetId)
                .orElseThrow(() -> new ApiException(FIELDSET_NOT_FOUND));
        FieldConfiguration configuration = fieldConfigurationRepository.findById(configurationId)
                .orElseThrow(() -> new ApiException("FieldConfiguration non trovata"));

        fieldSet.removeConfiguration(configuration);
        return fieldSetRepository.save(fieldSet);
    }

    public FieldSet updateConfigurationsOrder(Long fieldSetId, List<Long> orderedConfigIds) {
        FieldSet fieldSet = fieldSetRepository.findById(fieldSetId)
                .orElseThrow(() -> new ApiException(FIELDSET_NOT_FOUND));

        for (int i = 0; i < orderedConfigIds.size(); i++) {
            final int orderIndex = i;
            Long configId = orderedConfigIds.get(i);
            fieldSet.getFieldSetConfigurations().stream()
                    .filter(fsc -> fsc.getFieldConfiguration().getId().equals(configId))
                    .findFirst()
                    .ifPresent(fsc -> fsc.setOrderIndex(orderIndex));
        }

        return fieldSetRepository.save(fieldSet);
    }
    */
}

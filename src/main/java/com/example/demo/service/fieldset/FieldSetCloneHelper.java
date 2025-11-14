package com.example.demo.service.fieldset;

import com.example.demo.dto.FieldSetCreateDto;
import com.example.demo.dto.FieldSetEntryCreateDto;
import com.example.demo.entity.FieldConfiguration;
import com.example.demo.entity.FieldSet;
import com.example.demo.entity.FieldSetEntry;
import com.example.demo.entity.Project;
import com.example.demo.entity.Tenant;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.FieldSetRepository;
import com.example.demo.service.FieldConfigurationLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FieldSetCloneHelper {

    private final FieldSetRepository fieldSetRepository;
    private final FieldConfigurationLookup fieldConfigurationLookup;

    public FieldSet createFieldSet(FieldSetCreateDto dto, Tenant tenant, ScopeType scopeType, Project project) {
        List<Long> configIds = dto.entries().stream()
                .map(FieldSetEntryCreateDto::fieldConfigurationId)
                .toList();

        List<FieldConfiguration> configurations = fieldConfigurationLookup.getAll(configIds, tenant);

        Map<Long, FieldConfiguration> configMap = configurations.stream()
                .collect(Collectors.toMap(FieldConfiguration::getId, fc -> fc));

        FieldSet fieldSet = new FieldSet();
        fieldSet.setName(dto.name());
        fieldSet.setDescription(dto.description());
        fieldSet.setScope(scopeType);
        fieldSet.setTenant(tenant);
        fieldSet.setProject(project);
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
}









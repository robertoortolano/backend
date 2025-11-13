package com.example.demo.service.fieldset;

import com.example.demo.dto.FieldSetCreateDto;
import com.example.demo.dto.FieldSetEntryCreateDto;
import com.example.demo.entity.FieldConfiguration;
import com.example.demo.entity.FieldSet;
import com.example.demo.entity.FieldSetEntry;
import com.example.demo.entity.Tenant;
import com.example.demo.service.FieldConfigurationLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FieldSetUpdateHelper {

    private final FieldConfigurationLookup fieldConfigurationLookup;

    public FieldSetUpdateContext buildUpdateContext(Tenant tenant, FieldSet fieldSet, FieldSetCreateDto dto) {
        List<FieldSetEntryCreateDto> entryDtos = dto.entries() != null ? dto.entries() : List.of();

        Set<Long> existingConfigIds = fieldSet.getFieldSetEntries().stream()
                .map(entry -> entry.getFieldConfiguration().getId())
                .collect(Collectors.toCollection(HashSet::new));

        Set<Long> existingFieldIds = fieldSet.getFieldSetEntries().stream()
                .map(entry -> entry.getFieldConfiguration().getField().getId())
                .collect(Collectors.toCollection(HashSet::new));

        Set<Long> requestedConfigIds = entryDtos.stream()
                .map(FieldSetEntryCreateDto::fieldConfigurationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Set<Long> removedConfigIds = new HashSet<>(existingConfigIds);
        removedConfigIds.removeAll(requestedConfigIds);

        Set<Long> addedConfigIds = new HashSet<>(requestedConfigIds);
        addedConfigIds.removeAll(existingConfigIds);

        Set<Long> finalFieldIds = entryDtos.stream()
                .map(dtoEntry -> safeGetFieldId(dtoEntry.fieldConfigurationId(), tenant))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Set<Long> trulyNewFieldIds = finalFieldIds.stream()
                .filter(fieldId -> !existingFieldIds.contains(fieldId))
                .collect(Collectors.toCollection(HashSet::new));

        return new FieldSetUpdateContext(
                entryDtos,
                Collections.unmodifiableSet(existingConfigIds),
                Collections.unmodifiableSet(existingFieldIds),
                Collections.unmodifiableSet(requestedConfigIds),
                Collections.unmodifiableSet(removedConfigIds),
                Collections.unmodifiableSet(addedConfigIds),
                Collections.unmodifiableSet(finalFieldIds),
                Collections.unmodifiableSet(trulyNewFieldIds)
        );
    }

    public void applyFieldSetEntries(Tenant tenant, FieldSet fieldSet, List<FieldSetEntryCreateDto> entryDtos) {
        List<FieldSetEntry> entries = fieldSet.getFieldSetEntries();

        entries.removeIf(entry -> entryDtos.stream()
                .noneMatch(dto -> dto.fieldConfigurationId().equals(entry.getFieldConfiguration().getId())));

        for (int i = 0; i < entryDtos.size(); i++) {
            FieldSetEntryCreateDto dto = entryDtos.get(i);

            FieldSetEntry existingEntry = entries.stream()
                    .filter(e -> e.getFieldConfiguration().getId().equals(dto.fieldConfigurationId()))
                    .findFirst()
                    .orElse(null);

            if (existingEntry != null) {
                existingEntry.setOrderIndex(i);
            } else {
                FieldConfiguration fc = fieldConfigurationLookup.getById(dto.fieldConfigurationId(), tenant);

                FieldSetEntry newEntry = new FieldSetEntry();
                newEntry.setFieldSet(fieldSet);
                newEntry.setFieldConfiguration(fc);
                newEntry.setOrderIndex(i);

                entries.add(newEntry);
            }
        }
    }

    public FieldRemovalContext computeRemovalContext(
            Tenant tenant,
            FieldSet fieldSet,
            Set<Long> removedFieldConfigIds,
            Set<Long> addedFieldConfigIds
    ) {
        Set<Long> currentConfigIds = fieldSet.getFieldSetEntries().stream()
                .map(entry -> entry.getFieldConfiguration().getId())
                .collect(Collectors.toCollection(HashSet::new));

        Set<Long> remainingConfigIds = new HashSet<>(currentConfigIds);
        remainingConfigIds.removeAll(removedFieldConfigIds);

        if (addedFieldConfigIds != null) {
            remainingConfigIds.addAll(addedFieldConfigIds);
        }

        Set<Long> remainingFieldIds = remainingConfigIds.stream()
                .map(id -> safeGetFieldId(id, tenant))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Set<Long> removedFieldIds = new HashSet<>();
        for (Long fieldConfigId : removedFieldConfigIds) {
            Long fieldId = safeGetFieldId(fieldConfigId, tenant);
            if (fieldId != null && !remainingFieldIds.contains(fieldId)) {
                removedFieldIds.add(fieldId);
            }
        }

        return new FieldRemovalContext(
                Collections.unmodifiableSet(removedFieldIds),
                Collections.unmodifiableSet(remainingFieldIds)
        );
    }

    public List<String> resolveFieldConfigurationNames(Set<Long> fieldConfigIds, Tenant tenant) {
        List<String> names = new ArrayList<>();
        for (Long id : fieldConfigIds) {
            FieldConfiguration config = null;
            try {
                config = fieldConfigurationLookup.getById(id, tenant);
            } catch (Exception ignored) {
                // Gestione in fallback sotto
            }
            names.add(config != null ? config.getName() : "Configurazione " + id);
        }
        return names;
    }

    private Long safeGetFieldId(Long fieldConfigurationId, Tenant tenant) {
        if (fieldConfigurationId == null) {
            return null;
        }
        try {
            FieldConfiguration config = fieldConfigurationLookup.getById(fieldConfigurationId, tenant);
            return config != null ? config.getField().getId() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public record FieldSetUpdateContext(
            List<FieldSetEntryCreateDto> entryDtos,
            Set<Long> existingConfigIds,
            Set<Long> existingFieldIds,
            Set<Long> requestedConfigIds,
            Set<Long> removedConfigIds,
            Set<Long> addedConfigIds,
            Set<Long> finalFieldIds,
            Set<Long> trulyNewFieldIds
    ) {}

    public record FieldRemovalContext(
            Set<Long> removedFieldIds,
            Set<Long> remainingFieldIds
    ) {}
}








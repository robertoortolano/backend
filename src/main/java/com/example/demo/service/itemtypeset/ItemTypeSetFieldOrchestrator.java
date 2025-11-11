package com.example.demo.service.itemtypeset;

import com.example.demo.dto.ItemTypeConfigurationCreateDto;
import com.example.demo.entity.FieldSet;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Tenant;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.factory.FieldSetCloner;
import com.example.demo.repository.FieldSetRepository;
import com.example.demo.service.FieldSetLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ItemTypeSetFieldOrchestrator {

    private final FieldSetLookup fieldSetLookup;
    private final FieldSetRepository fieldSetRepository;
    private final FieldSetCloner fieldSetCloner;

    public void applyFieldSet(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            ItemTypeConfiguration configuration,
            ItemTypeConfigurationCreateDto dto,
            FieldSet defaultFieldSet
    ) {
        if (ScopeType.TENANT.equals(itemTypeSet.getScope())) {
            FieldSet requestedFieldSet = fieldSetLookup.getById(dto.fieldSetId(), tenant);
            configuration.setFieldSet(requestedFieldSet);
            return;
        }

        FieldSet baseFieldSet = defaultFieldSet != null ? defaultFieldSet : fieldSetLookup.getFirstDefault(tenant);
        if (baseFieldSet == null) {
            throw new ApiException("No default FieldSet found for tenant ID " + tenant.getId());
        }

        FieldSet requestedFieldSet = fieldSetLookup.getById(dto.fieldSetId(), tenant);
        boolean needsClone = configuration.getFieldSet() == null
                || configuration.getFieldSet().getId() == null
                || !configuration.getFieldSet().getId().equals(requestedFieldSet.getId());

        if (!needsClone) {
            return;
        }

        String suffix = configuration.getItemType() != null ? configuration.getItemType().getName() : "ItemTypeSet";
        FieldSet clonedFieldSet = fieldSetCloner.cloneFieldSet(baseFieldSet, " (copy for " + suffix + ")");
        fieldSetRepository.save(clonedFieldSet);
        configuration.setFieldSet(clonedFieldSet);
    }
}


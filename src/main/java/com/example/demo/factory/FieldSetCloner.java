package com.example.demo.factory;

import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.repository.FieldConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;

@Component
@RequiredArgsConstructor
public class FieldSetCloner {

    private final FieldConfigurationRepository fieldConfigurationRepository;

    public FieldSet cloneFieldSet(FieldSet original, String newNameSuffix) {
        return cloneFieldSet(original, newNameSuffix, null, null);
    }

    public FieldSet cloneFieldSet(FieldSet original, String newNameSuffix, ScopeType scope, Project project) {
        FieldSet copy = new FieldSet();

        copy.setName(original.getName() + newNameSuffix);
        copy.setDescription(original.getDescription());
        copy.setTenant(original.getTenant());
        copy.setScope(scope != null ? scope : original.getScope());
        copy.setDefaultFieldSet(false); // la copia non è default
        copy.setProject(project);
        copy.setFieldSetEntries(new ArrayList<>());

        for (FieldSetEntry originalEntry : original.getFieldSetEntries()) {
            FieldConfiguration origConfig = originalEntry.getFieldConfiguration();

            // Clona FieldConfiguration
            FieldConfiguration configCopy = new FieldConfiguration();
            configCopy.setField(origConfig.getField());
            configCopy.setDescription(origConfig.getDescription());
            configCopy.setFieldType(origConfig.getFieldType());
            configCopy.setScope(scope != null ? scope : origConfig.getScope());
            configCopy.setDefaultFieldConfiguration(origConfig.isDefaultFieldConfiguration());
            configCopy.setTenant(origConfig.getTenant());
            configCopy.setProject(project);
            configCopy.setOptions(new HashSet<>());

            // Clona opzioni
            for (FieldOption origOption : origConfig.getOptions()) {
                FieldOption optionCopy = new FieldOption();
                optionCopy.setLabel(origOption.getLabel());
                optionCopy.setValue(origOption.getValue());
                optionCopy.setEnabled(origOption.isEnabled());
                optionCopy.setOrderIndex(origOption.getOrderIndex());
                configCopy.getOptions().add(optionCopy);
            }

            // IMPORTANTE: Salva la FieldConfiguration prima di usarla nel FieldSetEntry
            configCopy = fieldConfigurationRepository.save(configCopy);

            // Crea FieldSetEntry
            FieldSetEntry entryCopy = new FieldSetEntry();
            entryCopy.setFieldSet(copy);
            entryCopy.setFieldConfiguration(configCopy);
            entryCopy.setOrderIndex(originalEntry.getOrderIndex());

            copy.getFieldSetEntries().add(entryCopy);
        }

        return copy;
    }
}

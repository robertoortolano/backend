package com.example.demo.factory;

import com.example.demo.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;

@Component
@RequiredArgsConstructor
public class FieldSetCloner {

    public FieldSet cloneFieldSet(FieldSet original, String newNameSuffix) {
        FieldSet copy = new FieldSet();

        copy.setName(original.getName() + newNameSuffix);
        copy.setDescription(original.getDescription());
        copy.setTenant(original.getTenant());
        copy.setScope(original.getScope());
        copy.setDefaultFieldSet(false); // la copia non è default
        copy.setFieldSetEntries(new ArrayList<>());

        for (FieldSetEntry originalEntry : original.getFieldSetEntries()) {
            FieldConfiguration origConfig = originalEntry.getFieldConfiguration();

            // Clona FieldConfiguration
            FieldConfiguration configCopy = new FieldConfiguration();
            configCopy.setField(origConfig.getField());
            configCopy.setDescription(origConfig.getDescription());
            configCopy.setFieldType(origConfig.getFieldType());
            configCopy.setScope(origConfig.getScope());
            configCopy.setDefaultFieldConfiguration(origConfig.isDefaultFieldConfiguration());
            configCopy.setOptions(new HashSet<>());

            // Clona opzioni
            for (FieldOption origOption : origConfig.getOptions()) {
                FieldOption optionCopy = new FieldOption();
                optionCopy.setLabel(origOption.getLabel());
                optionCopy.setValue(origOption.getValue());
                configCopy.getOptions().add(optionCopy);
            }

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

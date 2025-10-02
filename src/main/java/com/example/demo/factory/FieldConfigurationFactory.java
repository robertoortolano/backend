package com.example.demo.factory;

import com.example.demo.dto.FieldConfigurationCreateParams;
import com.example.demo.entity.*;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class FieldConfigurationFactory {

    public FieldConfiguration create(FieldConfigurationCreateParams params) {
        int index = 0;

        FieldConfiguration config = new FieldConfiguration();
        config.setName(params.name());
        config.setField(params.field());
        config.setDescription(params.description());
        config.setFieldType(params.fieldType());
        config.setScope(params.scopeType());
        config.setDefaultFieldConfiguration(params.isDefault());
        config.setTenant(params.tenant());

        Set<FieldOption> options = new HashSet<>();
        if (params.optionLabels() != null) {
            for (String label : params.optionLabels()) {
                FieldOption option = new FieldOption();
                option.setLabel(label);
                option.setValue(label);
                option.setOrderIndex(index++);
                option.setEnabled(true);
                options.add(option);
            }
        }
        config.setOptions(options);

        return config;
    }
}

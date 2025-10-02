package com.example.demo.initializer;

import com.example.demo.config.DefaultConfig;
import com.example.demo.config.DefaultConfigLoader;
import com.example.demo.dto.FieldConfigurationCreateParams;
import com.example.demo.entity.*;
import com.example.demo.enums.FieldType;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.factory.FieldConfigurationFactory;
import com.example.demo.factory.FieldSetEntryFactory;
import com.example.demo.repository.FieldConfigurationRepository;
import com.example.demo.repository.FieldRepository;
import com.example.demo.repository.FieldSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Order(2)
public class TenantFieldSetInitializer implements TenantInitializer {

    private final FieldSetRepository fieldSetRepository;
    private final FieldRepository fieldRepository;
    private final FieldConfigurationRepository fieldConfigurationRepository;
    private final FieldConfigurationFactory fieldConfigurationFactory;
    private final FieldSetEntryFactory fieldSetEntryFactory;

    @Override
    public void initialize(Tenant tenant) {
        DefaultConfig config = DefaultConfigLoader.load();

        Map<String, DefaultConfig.Field> fieldByName = config.getFields().stream()
                .collect(Collectors.toMap(DefaultConfig.Field::getName, Function.identity()));

        for (DefaultConfig.FieldSet fsDto : config.getFieldSets()) {
            FieldSet fieldSet = new FieldSet();
            fieldSet.setName(fsDto.getName());
            fieldSet.setDescription(fsDto.getDescription());
            fieldSet.setScope(ScopeType.GLOBAL);
            fieldSet.setTenant(tenant);
            fieldSet.setDefaultFieldSet(true);
            fieldSet.setFieldSetEntries(new ArrayList<>());

            fieldSet = fieldSetRepository.save(fieldSet); // salva per avere l'ID

            int index = 0;
            for (DefaultConfig.Field fieldRef : fsDto.getFields()) {
                Field field = fieldRepository.findByTenantAndName(tenant, fieldRef.getName())
                        .orElseThrow(() -> new ApiException("Field not found: " + fieldRef.getName()));
                DefaultConfig.Field def = fieldByName.get(fieldRef.getName());

                if (field == null || def == null) continue;

                // ✅ Usa la factory per la FieldConfiguration
                FieldConfiguration configEntity = fieldConfigurationFactory.create(
                        new FieldConfigurationCreateParams(
                                "Default " + field.getName() + "configuration",
                                field,
                                def.getDescription(),
                                FieldType.valueOf(def.getFieldType()),
                                def.getOptions(),
                                ScopeType.GLOBAL,
                                true,
                                tenant
                        )
                );

                configEntity = fieldConfigurationRepository.save(configEntity);

                // ✅ Usa la factory per la FieldSetEntry
                FieldSetEntry entry = fieldSetEntryFactory.create(
                        fieldSet,
                        configEntity,
                        index++
                );

                fieldSet.getFieldSetEntries().add(entry);
            }

            fieldSetRepository.save(fieldSet);
        }
    }

}

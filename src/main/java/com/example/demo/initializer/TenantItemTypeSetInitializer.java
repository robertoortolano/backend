package com.example.demo.initializer;

import com.example.demo.config.DefaultConfig;
import com.example.demo.config.DefaultConfigLoader;
import com.example.demo.entity.*;
import com.example.demo.enums.ItemTypeCategory;
import com.example.demo.enums.ScopeType;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Order(6)
public class TenantItemTypeSetInitializer implements TenantInitializer {

    private final ItemTypeSetRepository itemTypeSetRepository;
    private final ItemTypeRepository itemTypeRepository;
    private final ItemTypeConfigurationRepository itemTypeConfigurationRepository;
    private final WorkflowRepository workflowRepository;
    private final FieldSetRepository fieldSetRepository;

    @Override
    public void initialize(Tenant tenant) {
        DefaultConfig config = DefaultConfigLoader.load();

        Workflow defaultWorkflow = workflowRepository
                .findByTenantAndDefaultWorkflowTrue(tenant)
                .orElseThrow(() -> new IllegalStateException("Default workflow non trovato per tenant " + tenant.getId()));

        // Mappa nome -> definizione JSON (per categoria)
        Map<String, DefaultConfig.ItemType> itemTypeByName = config.getItemTypes().stream()
                .collect(Collectors.toMap(DefaultConfig.ItemType::getName, Function.identity()));

        for (DefaultConfig.ItemTypeSet setDto : config.getItemTypeSets()) {
            Set<ItemTypeConfiguration> configurations = setDto.getItemTypes().stream()
                    .map(itemTypeName -> {
                        ItemType itemType = itemTypeRepository.findByTenantAndName(tenant, itemTypeName);
                        if (itemType == null) return null;

                        DefaultConfig.ItemType itemTypeDef = itemTypeByName.get(itemTypeName);
                        if (itemTypeDef == null) return null;

                        ItemTypeConfiguration configuration = new ItemTypeConfiguration();
                        configuration.setItemType(itemType);
                        configuration.setCategory(ItemTypeCategory.valueOf(itemTypeDef.getCategory()));
                        configuration.setScope(ScopeType.GLOBAL);
                        configuration.setTenant(tenant);
                        configuration.setWorkflow(defaultWorkflow); // ✅ associazione al workflow
                        configuration.setFieldSet(fieldSetRepository.findFirstByTenantAndDefaultFieldSetTrue(tenant));

                        itemTypeConfigurationRepository.save(configuration);
                        return configuration;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            ItemTypeSet itemTypeSet = new ItemTypeSet();
            itemTypeSet.setName(setDto.getName());
            itemTypeSet.setTenant(tenant);
            itemTypeSet.setScope(ScopeType.GLOBAL);
            itemTypeSet.setDefaultItemTypeSet(true);
            itemTypeSet.setItemTypeConfigurations(configurations);

            itemTypeSetRepository.save(itemTypeSet);
        }
    }
}

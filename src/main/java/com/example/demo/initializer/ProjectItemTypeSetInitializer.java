package com.example.demo.initializer;

import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.factory.FieldSetCloner;
import com.example.demo.repository.FieldSetRepository;
import com.example.demo.repository.ItemTypeSetRepository;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Order(1)
public class ProjectItemTypeSetInitializer implements ProjectInitializer {

    private final ItemTypeSetRepository itemTypeSetRepository;
    private final FieldSetRepository fieldSetRepository;
    private final ProjectRepository projectRepository;
    private final TenantRepository tenantRepository;
    private final FieldSetCloner fieldSetCloner;

    @Override
    public void initialize(Project project, Tenant tenant) {
        // Trova l’ItemTypeSet di default per la tenant
        ItemTypeSet defaultSet = itemTypeSetRepository.findFirstByTenantAndDefaultItemTypeSetTrue(tenant);
        if (defaultSet == null) {
            throw new ApiException("No ItemType found");
        }

        ItemTypeSet newSet = new ItemTypeSet();
        newSet.setName("Item Set per progetto " + project.getName());
        newSet.setTenant(tenant);
        newSet.setScope(defaultSet.getScope());
        newSet.setDefaultItemTypeSet(false);

        // Nuovo set di configurazioni
        Set<ItemTypeConfiguration> newConfigs = new HashSet<>();

        for (ItemTypeConfiguration originalConfig : defaultSet.getItemTypeConfigurations()) {
            ItemTypeConfiguration copy = new ItemTypeConfiguration();
            copy.setCategory(originalConfig.getCategory());
            copy.setScope(originalConfig.getScope());
            copy.setDefaultItemTypeConfiguration(false);
            copy.setItemType(originalConfig.getItemType());
            copy.setTenant(tenant);
            copy.setWorkflow(originalConfig.getWorkflow());
            copy.setWorkers(new HashSet<>(originalConfig.getWorkers()));

            // Clona il FieldSet se presente
            if (originalConfig.getFieldSet() != null) {
                String suffix = " (copia per progetto " + project.getName() + ")";
                FieldSet clonedFieldSet = fieldSetCloner.cloneFieldSet(originalConfig.getFieldSet(), suffix);
                fieldSetRepository.save(clonedFieldSet);
                copy.setFieldSet(clonedFieldSet);
            }

            newConfigs.add(copy);
        }

        newSet.setItemTypeConfigurations(newConfigs);

        // Salva il nuovo ItemTypeSet
        itemTypeSetRepository.save(newSet);

        // Associa il nuovo ItemTypeSet al progetto
        project.setItemTypeSet(newSet);
        projectRepository.save(project);
    }
}



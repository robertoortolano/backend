package com.example.demo.initializer;

import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.factory.FieldSetCloner;
import com.example.demo.repository.FieldSetRepository;
import com.example.demo.repository.ItemTypeConfigurationRepository;
import com.example.demo.repository.ItemTypeSetRepository;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.service.ItemTypePermissionService;
import com.example.demo.service.ItemTypeSetPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Order(7)
public class ProjectItemTypeSetInitializer implements ProjectInitializer {

    private final ItemTypeSetRepository itemTypeSetRepository;
    private final ItemTypeConfigurationRepository itemTypeConfigurationRepository;
    private final FieldSetRepository fieldSetRepository;
    private final ProjectRepository projectRepository;
    private final FieldSetCloner fieldSetCloner;
    private final ItemTypePermissionService itemTypePermissionService;
    private final ItemTypeSetPermissionService itemTypeSetPermissionService;

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

            // Salva la configurazione per ottenere l'ID
            copy = itemTypeConfigurationRepository.save(copy);
            newConfigs.add(copy);
        }

        newSet.setItemTypeConfigurations(newConfigs);

        // Salva il nuovo ItemTypeSet
        newSet = itemTypeSetRepository.save(newSet);
        
        // Crea le permissions per ogni ItemTypeConfiguration copiata
        for (ItemTypeConfiguration config : newConfigs) {
            itemTypePermissionService.createPermissionsForItemTypeConfiguration(config);
        }
        
        // Crea tutte le permissions per l'ItemTypeSet (WORKER, STATUS_OWNER, FIELD_EDITOR, ecc.)
        itemTypeSetPermissionService.createPermissionsForItemTypeSet(newSet.getId(), tenant);

        // Associa il nuovo ItemTypeSet al progetto
        project.setItemTypeSet(newSet);
        projectRepository.save(project);
    }
}



package com.example.demo.initializer;

import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ItemTypeSetRepository;
import com.example.demo.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Order(7)
public class ProjectItemTypeSetInitializer implements ProjectInitializer {

    private final ItemTypeSetRepository itemTypeSetRepository;
    private final ProjectRepository projectRepository;

    @Override
    public void initialize(Project project, Tenant tenant) {
        // Trova l'ItemTypeSet di default per la tenant
        ItemTypeSet defaultSet = itemTypeSetRepository.findFirstByTenantAndDefaultItemTypeSetTrue(tenant);
        if (defaultSet == null) {
            throw new ApiException("No default ItemTypeSet found for tenant");
        }

        // Associa direttamente il set di default al progetto (globale e condiviso)
        project.setItemTypeSet(defaultSet);
        projectRepository.save(project);
    }
}



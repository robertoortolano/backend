package com.example.demo.service;

import com.example.demo.dto.ItemTypeConfigurationMigrationImpactDto;
import com.example.demo.dto.ItemTypeConfigurationMigrationRequest;
import com.example.demo.entity.FieldSet;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Workflow;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.CreatorPermissionRepository;
import com.example.demo.repository.ExecutorPermissionRepository;
import com.example.demo.repository.FieldOwnerPermissionRepository;
import com.example.demo.repository.FieldStatusPermissionRepository;
import com.example.demo.repository.ItemTypeConfigurationRepository;
import com.example.demo.repository.StatusOwnerPermissionRepository;
import com.example.demo.repository.WorkerPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ItemTypeConfigurationMigrationService {

    private final ItemTypeConfigurationRepository itemTypeConfigurationRepository;
    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final WorkerPermissionRepository workerPermissionRepository;
    private final CreatorPermissionRepository creatorPermissionRepository;
    private final WorkflowLookup workflowLookup;
    private final FieldSetLookup fieldSetLookup;
    private final ItemTypePermissionService itemTypePermissionService;
    private final ItemTypeConfigurationMigrationAnalysisService migrationAnalysisService;

    @Transactional(readOnly = true)
    public ItemTypeConfigurationMigrationImpactDto analyzeMigrationImpact(
            Tenant tenant,
            Long itemTypeConfigurationId,
            Long newFieldSetId,
            Long newWorkflowId
    ) {
        return migrationAnalysisService.analyzeMigrationImpact(tenant, itemTypeConfigurationId, newFieldSetId, newWorkflowId);
    }

    public void applyMigration(
            Tenant tenant,
            Long itemTypeConfigurationId,
            ItemTypeConfigurationMigrationRequest request
    ) {
        if (!request.isValid()) {
            throw new ApiException("Invalid request: preserveAllPreservable and removeAll cannot both be true");
        }

        ItemTypeConfiguration config = itemTypeConfigurationRepository.findById(itemTypeConfigurationId)
                .orElseThrow(() -> new ApiException("ItemTypeConfiguration not found: " + itemTypeConfigurationId));

        if (!config.getTenant().getId().equals(tenant.getId())) {
            throw new ApiException("ItemTypeConfiguration does not belong to tenant");
        }

        ItemTypeConfigurationMigrationImpactDto impact = migrationAnalysisService.analyzeMigrationImpact(
                tenant,
                itemTypeConfigurationId,
                request.newFieldSetId(),
                request.newWorkflowId()
        );

        Set<Long> permissionsToPreserve;
        if (Boolean.TRUE.equals(request.removeAll())) {
            permissionsToPreserve = new HashSet<>();
        } else if (Boolean.TRUE.equals(request.preserveAllPreservable())) {
            permissionsToPreserve = getAllPreservablePermissionIds(impact);
        } else if (request.preservePermissionIds() == null) {
            permissionsToPreserve = getAllPreservablePermissionIds(impact);
        } else {
            permissionsToPreserve = request.preservePermissionIds();
        }

        migrateFieldOwnerPermissions(config, impact, permissionsToPreserve);
        migrateStatusOwnerPermissions(config, impact, permissionsToPreserve);
        migrateFieldStatusPermissions(config, impact, permissionsToPreserve);
        migrateExecutorPermissions(config, impact, permissionsToPreserve);
        migrateWorkerPermissions(config, impact, permissionsToPreserve);
        migrateCreatorPermissions(config, impact, permissionsToPreserve);

        if (request.newFieldSetId() != null) {
            FieldSet newFieldSet = fieldSetLookup.getById(request.newFieldSetId(), tenant);
            config.setFieldSet(newFieldSet);
        }
        if (request.newWorkflowId() != null) {
            Workflow newWorkflow = workflowLookup.getByIdEntity(tenant, request.newWorkflowId());
            config.setWorkflow(newWorkflow);
        }
        itemTypeConfigurationRepository.save(config);

        createNewPermissions(config, impact, permissionsToPreserve);
    }

    private void migrateFieldOwnerPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve
    ) {
        impact.getFieldOwnerPermissions().forEach(permImpact -> {
            if (!permissionsToPreserve.contains(permImpact.getPermissionId())) {
                fieldOwnerPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        });
    }

    private void migrateStatusOwnerPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve
    ) {
        impact.getStatusOwnerPermissions().forEach(permImpact -> {
            if (!permissionsToPreserve.contains(permImpact.getPermissionId())) {
                statusOwnerPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        });
    }

    private void migrateFieldStatusPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve
    ) {
        impact.getFieldStatusPermissions().forEach(permImpact -> {
            if (!permissionsToPreserve.contains(permImpact.getPermissionId())) {
                fieldStatusPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        });
    }

    private void migrateExecutorPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve
    ) {
        impact.getExecutorPermissions().forEach(permImpact -> {
            if (!permissionsToPreserve.contains(permImpact.getPermissionId())) {
                executorPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        });
    }

    private void migrateWorkerPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve
    ) {
        if (impact.getWorkerPermissions() == null) {
            return;
        }
        impact.getWorkerPermissions().forEach(permImpact -> {
            if (!permissionsToPreserve.contains(permImpact.getPermissionId())) {
                workerPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        });
    }

    private void migrateCreatorPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve
    ) {
        if (impact.getCreatorPermissions() == null) {
            return;
        }
        impact.getCreatorPermissions().forEach(permImpact -> {
            if (!permissionsToPreserve.contains(permImpact.getPermissionId())) {
                creatorPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        });
    }

    private void createNewPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> preservedPermissionIds
    ) {
        itemTypePermissionService.createPermissionsForItemTypeConfiguration(config);
    }

    private Set<Long> getAllPreservablePermissionIds(ItemTypeConfigurationMigrationImpactDto impact) {
        Set<Long> preservable = new HashSet<>();

        impact.getFieldOwnerPermissions().stream()
                .filter(ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact::isCanBePreserved)
                .forEach(p -> preservable.add(p.getPermissionId()));

        impact.getStatusOwnerPermissions().stream()
                .filter(ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact::isCanBePreserved)
                .forEach(p -> preservable.add(p.getPermissionId()));

        impact.getFieldStatusPermissions().stream()
                .filter(ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact::isCanBePreserved)
                .forEach(p -> preservable.add(p.getPermissionId()));

        impact.getExecutorPermissions().stream()
                .filter(ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact::isCanBePreserved)
                .forEach(p -> preservable.add(p.getPermissionId()));

        if (impact.getWorkerPermissions() != null) {
            impact.getWorkerPermissions().stream()
                    .filter(ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact::isCanBePreserved)
                    .forEach(p -> preservable.add(p.getPermissionId()));
        }

        if (impact.getCreatorPermissions() != null) {
            impact.getCreatorPermissions().stream()
                    .filter(ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact::isCanBePreserved)
                    .forEach(p -> preservable.add(p.getPermissionId()));
        }

        return preservable;
    }
}


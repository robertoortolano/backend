package com.example.demo.service;

import com.example.demo.dto.ItemTypeConfigurationMigrationImpactDto;
import com.example.demo.dto.ItemTypeConfigurationMigrationRequest;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Tenant;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.CreatorPermissionRepository;
import com.example.demo.repository.ExecutorPermissionRepository;
import com.example.demo.repository.FieldOwnerPermissionRepository;
import com.example.demo.repository.FieldStatusPermissionRepository;
import com.example.demo.repository.ItemTypeConfigurationRepository;
import com.example.demo.repository.ItemTypeSetRepository;
import com.example.demo.repository.StatusOwnerPermissionRepository;
import com.example.demo.repository.WorkerPermissionRepository;
import com.example.demo.service.PermissionAssignmentService;
import com.example.demo.service.ProjectPermissionAssignmentService;
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
    private final ItemTypePermissionService itemTypePermissionService;
    private final ItemTypeConfigurationMigrationAnalysisService migrationAnalysisService;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;
    private final ItemTypeSetRepository itemTypeSetRepository;

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

        // Ottieni l'ItemTypeSet per questa configurazione per rimuovere le assegnazioni di progetto
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByItemTypeConfigurations_IdAndTenant(
                itemTypeConfigurationId, tenant).stream()
                .findFirst()
                .orElse(null);

        // Rimuovi le permission non preservate (incluse le assegnazioni)
        // NOTA: NON aggiorniamo qui il workflow/fieldset della configurazione,
        // perché questo viene fatto in updateItemTypeSet che chiama anche il cleanup automatico
        // per rimuovere le permission obsolete che non sono nell'impact report
        migrateFieldOwnerPermissions(config, impact, permissionsToPreserve, tenant, itemTypeSet);
        migrateStatusOwnerPermissions(config, impact, permissionsToPreserve, tenant, itemTypeSet);
        migrateFieldStatusPermissions(config, impact, permissionsToPreserve, tenant, itemTypeSet);
        migrateExecutorPermissions(config, impact, permissionsToPreserve, tenant, itemTypeSet);
        migrateWorkerPermissions(config, impact, permissionsToPreserve, tenant, itemTypeSet);
        migrateCreatorPermissions(config, impact, permissionsToPreserve, tenant, itemTypeSet);

        // NON aggiorniamo qui workflow/fieldset - questo viene fatto in updateItemTypeSet
        // che chiama anche il cleanup automatico per rimuovere le permission obsolete
        // itemTypeConfigurationRepository.save(config);
        // createNewPermissions(config, impact, permissionsToPreserve);
    }

    private void migrateFieldOwnerPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve,
            Tenant tenant,
            ItemTypeSet itemTypeSet
    ) {
        impact.getFieldOwnerPermissions().forEach(permImpact -> {
            if (!permissionsToPreserve.contains(permImpact.getPermissionId())) {
                // Rimuovi prima le assegnazioni (ruoli e grant)
                permissionAssignmentService.deleteAssignment("FieldOwnerPermission", permImpact.getPermissionId(), tenant);
                deleteProjectAssignments(itemTypeSet, permImpact.getPermissionId(), "FieldOwnerPermission", tenant);
                // Poi rimuovi la permission
                fieldOwnerPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        });
    }

    private void migrateStatusOwnerPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve,
            Tenant tenant,
            ItemTypeSet itemTypeSet
    ) {
        impact.getStatusOwnerPermissions().forEach(permImpact -> {
            if (!permissionsToPreserve.contains(permImpact.getPermissionId())) {
                // Rimuovi prima le assegnazioni (ruoli e grant)
                permissionAssignmentService.deleteAssignment("StatusOwnerPermission", permImpact.getPermissionId(), tenant);
                deleteProjectAssignments(itemTypeSet, permImpact.getPermissionId(), "StatusOwnerPermission", tenant);
                // Poi rimuovi la permission
                statusOwnerPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        });
    }

    private void migrateFieldStatusPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve,
            Tenant tenant,
            ItemTypeSet itemTypeSet
    ) {
        impact.getFieldStatusPermissions().forEach(permImpact -> {
            if (!permissionsToPreserve.contains(permImpact.getPermissionId())) {
                // Rimuovi prima le assegnazioni (ruoli e grant)
                permissionAssignmentService.deleteAssignment("FieldStatusPermission", permImpact.getPermissionId(), tenant);
                deleteProjectAssignments(itemTypeSet, permImpact.getPermissionId(), "FieldStatusPermission", tenant);
                // Poi rimuovi la permission
                fieldStatusPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        });
    }

    private void migrateExecutorPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve,
            Tenant tenant,
            ItemTypeSet itemTypeSet
    ) {
        impact.getExecutorPermissions().forEach(permImpact -> {
            if (!permissionsToPreserve.contains(permImpact.getPermissionId())) {
                // Rimuovi prima le assegnazioni (ruoli e grant)
                permissionAssignmentService.deleteAssignment("ExecutorPermission", permImpact.getPermissionId(), tenant);
                deleteProjectAssignments(itemTypeSet, permImpact.getPermissionId(), "ExecutorPermission", tenant);
                // Poi rimuovi la permission
                executorPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        });
    }

    private void migrateWorkerPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve,
            Tenant tenant,
            ItemTypeSet itemTypeSet
    ) {
        if (impact.getWorkerPermissions() == null) {
            return;
        }
        impact.getWorkerPermissions().forEach(permImpact -> {
            if (!permissionsToPreserve.contains(permImpact.getPermissionId())) {
                // Rimuovi prima le assegnazioni (ruoli e grant)
                permissionAssignmentService.deleteAssignment("WorkerPermission", permImpact.getPermissionId(), tenant);
                deleteProjectAssignments(itemTypeSet, permImpact.getPermissionId(), "WorkerPermission", tenant);
                // Poi rimuovi la permission
                workerPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        });
    }

    private void migrateCreatorPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve,
            Tenant tenant,
            ItemTypeSet itemTypeSet
    ) {
        if (impact.getCreatorPermissions() == null) {
            return;
        }
        impact.getCreatorPermissions().forEach(permImpact -> {
            if (!permissionsToPreserve.contains(permImpact.getPermissionId())) {
                // Rimuovi prima le assegnazioni (ruoli e grant)
                permissionAssignmentService.deleteAssignment("CreatorPermission", permImpact.getPermissionId(), tenant);
                deleteProjectAssignments(itemTypeSet, permImpact.getPermissionId(), "CreatorPermission", tenant);
                // Poi rimuovi la permission
                creatorPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        });
    }

    private void deleteProjectAssignments(
            ItemTypeSet itemTypeSet,
            Long permissionId,
            String permissionType,
            Tenant tenant
    ) {
        if (itemTypeSet == null) {
            return;
        }

        if (itemTypeSet.getProject() != null) {
            projectPermissionAssignmentService.deleteProjectAssignment(
                    permissionType,
                    permissionId,
                    itemTypeSet.getProject().getId(),
                    tenant
            );
            return;
        }

        if (itemTypeSet.getProjectsAssociation() == null) {
            return;
        }

        for (com.example.demo.entity.Project project : itemTypeSet.getProjectsAssociation()) {
            projectPermissionAssignmentService.deleteProjectAssignment(
                    permissionType,
                    permissionId,
                    project.getId(),
                    tenant
            );
        }
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


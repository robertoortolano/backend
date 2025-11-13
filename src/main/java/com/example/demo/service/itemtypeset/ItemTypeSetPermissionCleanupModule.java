package com.example.demo.service.itemtypeset;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.PermissionAssignmentService;
import com.example.demo.service.ProjectPermissionAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class ItemTypeSetPermissionCleanupModule {

    private final ItemTypeSetRepository itemTypeSetRepository;
    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;
    private final TransitionRepository transitionRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final com.example.demo.repository.FieldSetRepository fieldSetRepository;

    public void removeOrphanedPermissionsForItemTypeConfigurations(
            Tenant tenant,
            Long itemTypeSetId,
            Set<Long> removedItemTypeConfigurationIds,
            Set<Long> preservedPermissionIds
    ) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByIdAndTenant(itemTypeSetId, tenant)
                .orElseThrow(() -> new com.example.demo.exception.ApiException("ItemTypeSet not found: " + itemTypeSetId));

        List<ItemTypeConfiguration> configsToRemove = itemTypeSet.getItemTypeConfigurations().stream()
                .filter(config -> removedItemTypeConfigurationIds.contains(config.getId()))
                .collect(Collectors.toList());

        if (configsToRemove.isEmpty()) {
            return;
        }

        for (ItemTypeConfiguration config : configsToRemove) {
            deleteFieldOwnerPermissions(tenant, itemTypeSet, preservedPermissionIds, config);
            deleteStatusOwnerPermissions(tenant, itemTypeSet, preservedPermissionIds, config);
            deleteFieldStatusPermissions(tenant, itemTypeSet, preservedPermissionIds, config);
            deleteExecutorPermissions(tenant, itemTypeSet, preservedPermissionIds, config);
        }
    }

    private void deleteFieldOwnerPermissions(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            Set<Long> preservedPermissionIds,
            ItemTypeConfiguration config
    ) {
        List<FieldOwnerPermission> fieldOwnerPermissions = fieldOwnerPermissionRepository.findAllByItemTypeConfiguration(config);
        for (FieldOwnerPermission perm : fieldOwnerPermissions) {
            if (isPreserved(preservedPermissionIds, perm.getId())) {
                continue;
            }
            try {
                permissionAssignmentService.deleteAssignment("FieldOwnerPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "FieldOwnerPermission", tenant);
                // Verifica se la permission esiste ancora prima di eliminarla (idempotenza)
                if (fieldOwnerPermissionRepository.existsById(perm.getId())) {
                    try {
                        fieldOwnerPermissionRepository.delete(perm);
                    } catch (Exception e) {
                        // Se la permission è già stata eliminata, ignora (idempotenza)
                        // log.warn("Errore durante la rimozione di FieldOwnerPermission {}: {}", perm.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                // Se la permission o l'assegnazione sono già state eliminate, ignora l'eccezione (idempotenza)
                // Log solo per debug, ma non interrompere il processo
                // log.warn("Errore durante la rimozione di FieldOwnerPermission {}: {}", perm.getId(), e.getMessage());
            }
        }
    }

    private void deleteStatusOwnerPermissions(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            Set<Long> preservedPermissionIds,
            ItemTypeConfiguration config
    ) {
        List<StatusOwnerPermission> statusOwnerPermissions = statusOwnerPermissionRepository.findByItemTypeConfigurationIdAndTenant(config.getId(), itemTypeSet.getTenant());
        for (StatusOwnerPermission perm : statusOwnerPermissions) {
            if (isPreserved(preservedPermissionIds, perm.getId())) {
                continue;
            }
            try {
                permissionAssignmentService.deleteAssignment("StatusOwnerPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "StatusOwnerPermission", tenant);
                // Verifica se la permission esiste ancora prima di eliminarla (idempotenza)
                if (statusOwnerPermissionRepository.existsById(perm.getId())) {
                    try {
                        statusOwnerPermissionRepository.delete(perm);
                    } catch (Exception e) {
                        // Se la permission è già stata eliminata, ignora (idempotenza)
                        // log.warn("Errore durante la rimozione di StatusOwnerPermission {}: {}", perm.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                // Se la permission o l'assegnazione sono già state eliminate, ignora l'eccezione (idempotenza)
                // log.warn("Errore durante la rimozione di StatusOwnerPermission {}: {}", perm.getId(), e.getMessage());
            }
        }
    }

    private void deleteFieldStatusPermissions(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            Set<Long> preservedPermissionIds,
            ItemTypeConfiguration config
    ) {
        List<FieldStatusPermission> fieldStatusPermissions = fieldStatusPermissionRepository.findByItemTypeConfigurationIdAndTenant(config.getId(), itemTypeSet.getTenant());
        for (FieldStatusPermission perm : fieldStatusPermissions) {
            if (isPreserved(preservedPermissionIds, perm.getId())) {
                continue;
            }
            try {
                permissionAssignmentService.deleteAssignment("FieldStatusPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "FieldStatusPermission", tenant);
                // Verifica se la permission esiste ancora prima di eliminarla (idempotenza)
                if (fieldStatusPermissionRepository.existsById(perm.getId())) {
                    try {
                        fieldStatusPermissionRepository.delete(perm);
                    } catch (Exception e) {
                        // Se la permission è già stata eliminata, ignora (idempotenza)
                        // log.warn("Errore durante la rimozione di FieldStatusPermission {}: {}", perm.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                // Se la permission o l'assegnazione sono già state eliminate, ignora l'eccezione (idempotenza)
                // log.warn("Errore durante la rimozione di FieldStatusPermission {}: {}", perm.getId(), e.getMessage());
            }
        }
    }

    private void deleteExecutorPermissions(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            Set<Long> preservedPermissionIds,
            ItemTypeConfiguration config
    ) {
        List<ExecutorPermission> executorPermissions = executorPermissionRepository.findAllByItemTypeConfiguration(config);
        for (ExecutorPermission perm : executorPermissions) {
            if (isPreserved(preservedPermissionIds, perm.getId())) {
                continue;
            }
            try {
                permissionAssignmentService.deleteAssignment("ExecutorPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "ExecutorPermission", tenant);
                // Verifica se la permission esiste ancora prima di eliminarla (idempotenza)
                if (executorPermissionRepository.existsById(perm.getId())) {
                    try {
                        executorPermissionRepository.delete(perm);
                    } catch (Exception e) {
                        // Se la permission è già stata eliminata, ignora (idempotenza)
                        // log.warn("Errore durante la rimozione di ExecutorPermission {}: {}", perm.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                // Se la permission o l'assegnazione sono già state eliminate, ignora l'eccezione (idempotenza)
                // log.warn("Errore durante la rimozione di ExecutorPermission {}: {}", perm.getId(), e.getMessage());
            }
        }
    }

    private void deleteProjectAssignments(
            ItemTypeSet itemTypeSet,
            Long permissionId,
            String permissionType,
            Tenant tenant
    ) {
        try {
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

            for (Project project : itemTypeSet.getProjectsAssociation()) {
                try {
                    projectPermissionAssignmentService.deleteProjectAssignment(
                            permissionType,
                            permissionId,
                            project.getId(),
                            tenant
                    );
                } catch (Exception e) {
                    // Se l'assegnazione di progetto è già stata eliminata, ignora l'eccezione (idempotenza)
                    // log.warn("Errore durante la rimozione di ProjectAssignment per permission {} nel progetto {}: {}", 
                    //          permissionId, project.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            // Se l'assegnazione di progetto è già stata eliminata, ignora l'eccezione (idempotenza)
            // log.warn("Errore durante la rimozione di ProjectAssignment per permission {}: {}", permissionId, e.getMessage());
        }
    }

    private boolean isPreserved(Set<Long> preservedPermissionIds, Long permissionId) {
        return preservedPermissionIds != null && preservedPermissionIds.contains(permissionId);
    }

    /**
     * Rimuove le permission obsolete quando cambiano workflow o fieldset in una configurazione esistente.
     * Questo metodo viene chiamato prima di creare le nuove permission per assicurarsi che le permission vecchie
     * vengano rimosse correttamente.
     * 
     * @param tenant Tenant corrente
     * @param itemTypeSet ItemTypeSet a cui appartiene la configurazione
     * @param existingConfig Configurazione esistente (prima dell'update)
     * @param newConfig Configurazione aggiornata (dopo l'update)
     */
    public void removeObsoletePermissionsForUpdatedConfiguration(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            ItemTypeConfiguration existingConfig,
            ItemTypeConfiguration newConfig
    ) {
        // Verifica se workflow o fieldset sono cambiati confrontando gli ID
        // Usa gli ID direttamente per evitare problemi di lazy loading
        Long existingWorkflowId = existingConfig.getWorkflow() != null ? existingConfig.getWorkflow().getId() : null;
        Long newWorkflowId = newConfig.getWorkflow() != null ? newConfig.getWorkflow().getId() : null;
        Long existingFieldSetId = existingConfig.getFieldSet() != null ? existingConfig.getFieldSet().getId() : null;
        Long newFieldSetId = newConfig.getFieldSet() != null ? newConfig.getFieldSet().getId() : null;
        
        boolean workflowChanged = !Objects.equals(existingWorkflowId, newWorkflowId);
        boolean fieldSetChanged = !Objects.equals(existingFieldSetId, newFieldSetId);

        if (!workflowChanged && !fieldSetChanged) {
            // Nessun cambiamento: non c'è nulla da rimuovere
            return;
        }

        // Rimuovi le permission obsolete basate sui cambiamenti
        if (workflowChanged) {
            removeObsoleteExecutorPermissions(tenant, itemTypeSet, existingConfig, newConfig);
            removeObsoleteStatusOwnerPermissions(tenant, itemTypeSet, existingConfig, newConfig);
            // Field status permissions dipendono sia da workflow che da fieldset
            // Se cambia solo il workflow, rimuovi quelle per status che non appartengono più al nuovo workflow
            if (newConfig.getWorkflow() != null) {
                removeObsoleteFieldStatusPermissions(tenant, itemTypeSet, existingConfig, newConfig, workflowChanged, fieldSetChanged);
            }
        }

        if (fieldSetChanged) {
            removeObsoleteFieldOwnerPermissions(tenant, itemTypeSet, existingConfig, newConfig);
            // Field status permissions dipendono sia da workflow che da fieldset
            // Se cambia solo il fieldset, rimuovi quelle per field che non appartengono più al nuovo fieldset
            // Il cleanup delle FieldStatusPermission viene chiamato anche se il workflow è null,
            // perché le FieldStatusPermission richiedono sia field che workflow status
            if (newConfig.getFieldSet() != null) {
                removeObsoleteFieldStatusPermissions(tenant, itemTypeSet, existingConfig, newConfig, workflowChanged, fieldSetChanged);
            }
        }
    }

    /**
     * Rimuove le executor permissions obsolete quando cambia il workflow.
     * Rimuove tutte le executor permissions per transition che non appartengono più al nuovo workflow.
     */
    private void removeObsoleteExecutorPermissions(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            ItemTypeConfiguration existingConfig,
            ItemTypeConfiguration newConfig
    ) {
        if (newConfig.getWorkflow() == null) {
            // Se il nuovo workflow è null, rimuovi tutte le executor permissions
            List<ExecutorPermission> executorPermissions = executorPermissionRepository.findAllByItemTypeConfiguration(existingConfig);
            for (ExecutorPermission perm : executorPermissions) {
                permissionAssignmentService.deleteAssignment("ExecutorPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "ExecutorPermission", tenant);
                executorPermissionRepository.delete(perm);
            }
            return;
        }

        // Ottieni tutte le transition del nuovo workflow
        List<Transition> newTransitions = transitionRepository.findByWorkflowAndTenant(newConfig.getWorkflow(), tenant);
        Set<Long> newTransitionIds = newTransitions.stream()
                .map(Transition::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Ottieni tutte le executor permissions esistenti
        List<ExecutorPermission> executorPermissions = executorPermissionRepository.findAllByItemTypeConfiguration(existingConfig);
        
        // Rimuovi le permission per transition che non appartengono più al nuovo workflow
        for (ExecutorPermission perm : executorPermissions) {
            if (perm.getTransition() == null || perm.getTransition().getId() == null) {
                // Permission senza transition: rimuovi
                permissionAssignmentService.deleteAssignment("ExecutorPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "ExecutorPermission", tenant);
                executorPermissionRepository.delete(perm);
            } else if (!newTransitionIds.contains(perm.getTransition().getId())) {
                // Transition non appartiene più al nuovo workflow: rimuovi
                permissionAssignmentService.deleteAssignment("ExecutorPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "ExecutorPermission", tenant);
                executorPermissionRepository.delete(perm);
            }
        }
    }

    /**
     * Rimuove le status owner permissions obsolete quando cambia il workflow.
     * Rimuove tutte le status owner permissions per workflow status che non appartengono più al nuovo workflow.
     */
    private void removeObsoleteStatusOwnerPermissions(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            ItemTypeConfiguration existingConfig,
            ItemTypeConfiguration newConfig
    ) {
        if (newConfig.getWorkflow() == null) {
            // Se il nuovo workflow è null, rimuovi tutte le status owner permissions
            List<StatusOwnerPermission> statusOwnerPermissions = statusOwnerPermissionRepository
                    .findByItemTypeConfigurationIdAndTenant(existingConfig.getId(), tenant);
            for (StatusOwnerPermission perm : statusOwnerPermissions) {
                permissionAssignmentService.deleteAssignment("StatusOwnerPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "StatusOwnerPermission", tenant);
                statusOwnerPermissionRepository.delete(perm);
            }
            return;
        }

        // Ottieni tutti i workflow status del nuovo workflow
        List<WorkflowStatus> newWorkflowStatuses = workflowStatusRepository.findByWorkflowAndTenant(newConfig.getWorkflow(), tenant);
        Set<Long> newWorkflowStatusIds = newWorkflowStatuses.stream()
                .map(WorkflowStatus::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Ottieni tutte le status owner permissions esistenti
        List<StatusOwnerPermission> statusOwnerPermissions = statusOwnerPermissionRepository
                .findByItemTypeConfigurationIdAndTenant(existingConfig.getId(), tenant);

        // Rimuovi le permission per workflow status che non appartengono più al nuovo workflow
        for (StatusOwnerPermission perm : statusOwnerPermissions) {
            if (perm.getWorkflowStatus() == null || perm.getWorkflowStatus().getId() == null) {
                // Permission senza workflow status: rimuovi
                permissionAssignmentService.deleteAssignment("StatusOwnerPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "StatusOwnerPermission", tenant);
                statusOwnerPermissionRepository.delete(perm);
            } else if (!newWorkflowStatusIds.contains(perm.getWorkflowStatus().getId())) {
                // Workflow status non appartiene più al nuovo workflow: rimuovi
                permissionAssignmentService.deleteAssignment("StatusOwnerPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "StatusOwnerPermission", tenant);
                statusOwnerPermissionRepository.delete(perm);
            }
        }
    }

    /**
     * Rimuove le field owner permissions obsolete quando cambia il fieldset.
     * Rimuove tutte le field owner permissions per field che non appartengono più al nuovo fieldset.
     */
    private void removeObsoleteFieldOwnerPermissions(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            ItemTypeConfiguration existingConfig,
            ItemTypeConfiguration newConfig
    ) {
        if (newConfig.getFieldSet() == null) {
            // Se il nuovo fieldset è null, rimuovi tutte le field owner permissions
            List<FieldOwnerPermission> fieldOwnerPermissions = fieldOwnerPermissionRepository
                    .findAllByItemTypeConfiguration(existingConfig);
            for (FieldOwnerPermission perm : fieldOwnerPermissions) {
                permissionAssignmentService.deleteAssignment("FieldOwnerPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "FieldOwnerPermission", tenant);
                fieldOwnerPermissionRepository.delete(perm);
            }
            return;
        }

        // Carica il fieldset con i fieldSetEntries per assicurarsi di avere tutti i field
        // Il fieldset potrebbe essere lazy-loaded, quindi lo ricarichiamo esplicitamente con EntityGraph
        FieldSet fieldSet = fieldSetRepository.findByIdAndTenantWithEntries(newConfig.getFieldSet().getId(), tenant)
                .orElse(null);
        if (fieldSet == null || fieldSet.getFieldSetEntries() == null || fieldSet.getFieldSetEntries().isEmpty()) {
            // Se il nuovo fieldset è vuoto, rimuovi tutte le field owner permissions
            List<FieldOwnerPermission> fieldOwnerPermissions = fieldOwnerPermissionRepository
                    .findAllByItemTypeConfiguration(existingConfig);
            for (FieldOwnerPermission perm : fieldOwnerPermissions) {
                permissionAssignmentService.deleteAssignment("FieldOwnerPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "FieldOwnerPermission", tenant);
                fieldOwnerPermissionRepository.delete(perm);
            }
            return;
        }

        // Ottieni tutti i field ID del nuovo fieldset
        Set<Long> newFieldIds = fieldSet.getFieldSetEntries().stream()
                .map(entry -> entry.getFieldConfiguration() != null && entry.getFieldConfiguration().getField() != null
                        ? entry.getFieldConfiguration().getField().getId()
                        : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Ottieni tutte le field owner permissions esistenti
        List<FieldOwnerPermission> fieldOwnerPermissions = fieldOwnerPermissionRepository
                .findAllByItemTypeConfiguration(existingConfig);

        // Rimuovi le permission per field che non appartengono più al nuovo fieldset
        for (FieldOwnerPermission perm : fieldOwnerPermissions) {
            if (perm.getField() == null || perm.getField().getId() == null) {
                // Permission senza field: rimuovi
                permissionAssignmentService.deleteAssignment("FieldOwnerPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "FieldOwnerPermission", tenant);
                fieldOwnerPermissionRepository.delete(perm);
            } else if (!newFieldIds.contains(perm.getField().getId())) {
                // Field non appartiene più al nuovo fieldset: rimuovi
                permissionAssignmentService.deleteAssignment("FieldOwnerPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "FieldOwnerPermission", tenant);
                fieldOwnerPermissionRepository.delete(perm);
            }
        }
    }

    /**
     * Rimuove le field status permissions obsolete quando cambiano workflow o fieldset.
     * Rimuove tutte le field status permissions per field/status che non appartengono più al nuovo fieldset/workflow.
     */
    private void removeObsoleteFieldStatusPermissions(
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            ItemTypeConfiguration existingConfig,
            ItemTypeConfiguration newConfig,
            boolean workflowChanged,
            boolean fieldSetChanged
    ) {
        if (newConfig.getWorkflow() == null || newConfig.getFieldSet() == null) {
            // Se workflow o fieldset sono null, rimuovi tutte le field status permissions
            List<FieldStatusPermission> fieldStatusPermissions = fieldStatusPermissionRepository
                    .findByItemTypeConfigurationIdAndTenant(existingConfig.getId(), tenant);
            for (FieldStatusPermission perm : fieldStatusPermissions) {
                permissionAssignmentService.deleteAssignment("FieldStatusPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "FieldStatusPermission", tenant);
                fieldStatusPermissionRepository.delete(perm);
            }
            return;
        }

        // Ottieni tutti i field ID del nuovo fieldset solo se il fieldset è cambiato
        Set<Long> newFieldIds = null;
        if (fieldSetChanged) {
            // Carica il fieldset con i fieldSetEntries per assicurarsi di avere tutti i field
            // Il fieldset potrebbe essere lazy-loaded, quindi lo ricarichiamo esplicitamente con EntityGraph
            FieldSet fieldSet = fieldSetRepository.findByIdAndTenantWithEntries(newConfig.getFieldSet().getId(), tenant)
                    .orElse(null);
            if (fieldSet != null && fieldSet.getFieldSetEntries() != null && !fieldSet.getFieldSetEntries().isEmpty()) {
                newFieldIds = fieldSet.getFieldSetEntries().stream()
                        .map(entry -> entry.getFieldConfiguration() != null && entry.getFieldConfiguration().getField() != null
                                ? entry.getFieldConfiguration().getField().getId()
                                : null)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
            }
        }

        // Ottieni tutti i workflow status ID del nuovo workflow solo se il workflow è cambiato
        Set<Long> newWorkflowStatusIds = null;
        if (workflowChanged) {
            List<WorkflowStatus> newWorkflowStatuses = workflowStatusRepository.findByWorkflowAndTenant(newConfig.getWorkflow(), tenant);
            newWorkflowStatusIds = newWorkflowStatuses.stream()
                    .map(WorkflowStatus::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        // Ottieni tutte le field status permissions esistenti
        List<FieldStatusPermission> fieldStatusPermissions = fieldStatusPermissionRepository
                .findByItemTypeConfigurationIdAndTenant(existingConfig.getId(), tenant);

        // Rimuovi le permission per field/status che non appartengono più al nuovo fieldset/workflow
        for (FieldStatusPermission perm : fieldStatusPermissions) {
            boolean fieldObsolete = false;
            boolean statusObsolete = false;

            // Verifica se il field è obsoleto (solo se il fieldset è cambiato)
            if (fieldSetChanged) {
                if (newFieldIds == null || newFieldIds.isEmpty()) {
                    // Se il nuovo fieldset è vuoto o null, tutte le permission sono obsolete
                    fieldObsolete = true;
                } else {
                    fieldObsolete = perm.getField() == null || perm.getField().getId() == null
                            || !newFieldIds.contains(perm.getField().getId());
                }
            }

            // Verifica se lo status è obsoleto (solo se il workflow è cambiato)
            if (workflowChanged) {
                if (newWorkflowStatusIds == null || newWorkflowStatusIds.isEmpty()) {
                    // Se il nuovo workflow non ha workflow status, tutte le permission sono obsolete
                    statusObsolete = true;
                } else {
                    statusObsolete = perm.getWorkflowStatus() == null || perm.getWorkflowStatus().getId() == null
                            || !newWorkflowStatusIds.contains(perm.getWorkflowStatus().getId());
                }
            }

            // Rimuovi la permission se field o status sono obsoleti
            if (fieldObsolete || statusObsolete) {
                // Field o status non appartengono più al nuovo fieldset/workflow: rimuovi
                permissionAssignmentService.deleteAssignment("FieldStatusPermission", perm.getId(), tenant);
                deleteProjectAssignments(itemTypeSet, perm.getId(), "FieldStatusPermission", tenant);
                fieldStatusPermissionRepository.delete(perm);
            }
        }
    }
}





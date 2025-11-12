package com.example.demo.service;

import com.example.demo.dto.ItemTypeConfigurationCreateDto;
import com.example.demo.dto.ItemTypeConfigurationRemovalImpactDto;
import com.example.demo.dto.ItemTypeSetCreateDto;
import com.example.demo.dto.ItemTypeSetUpdateDto;
import com.example.demo.dto.ItemTypeSetViewDto;
import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.*;
import com.example.demo.service.itemtypeset.ItemTypeSetFieldOrchestrator;
import com.example.demo.service.itemtypeset.ItemTypeSetPermissionOrchestrator;
import com.example.demo.service.itemtypeset.ItemTypeSetWorkflowOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ItemTypeSetService {

    private final ItemTypeSetRepository itemTypeSetRepository;
    private final ProjectRepository projectRepository;

    private final ItemTypeConfigurationRepository itemTypeConfigurationRepository;

    private final FieldSetLookup fieldSetLookup;
    private final ProjectLookup projectLookup;
    private final ItemTypeSetLookup itemTypeSetLookup;

    private final DtoMapperFacade dtoMapper;

    private final ItemTypeSetWorkflowOrchestrator workflowOrchestrator;
    private final ItemTypeSetFieldOrchestrator fieldOrchestrator;
    private final ItemTypeSetPermissionOrchestrator permissionOrchestrator;
    
    // Repository per permission
    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    // Servizi per PermissionAssignment (nuova struttura)
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;
    
    // Repository per Permission (necessari per eliminare PermissionAssignment)
    private final WorkerPermissionRepository workerPermissionRepository;
    private final CreatorPermissionRepository creatorPermissionRepository;
    
    private static final String ITEMTYPESET_NOT_FOUND = "ItemTypeSet not found";


    private ItemTypeSetViewDto createItemTypeSet(
            Tenant tenant,
            ItemTypeSetCreateDto dto,
            ScopeType scopeType,
            Project project
    ) {

        if (dto.itemTypeConfigurations().isEmpty())
            throw new ApiException("There must be at least an item type");

        ItemTypeSet set = new ItemTypeSet();
        set.setScope(scopeType);
        set.setTenant(tenant);
        set.setName(dto.name());

        if (!scopeType.equals(ScopeType.TENANT)) {
            if (project == null) throw new ApiException("Project must be specified for non-global ItemTypeSet");
            set.setProject(project);
            set.getProjectsAssociation().add(project);
        }

        // Per ItemTypeSet di progetto, non serve il FieldSet di default
        // Il FieldSet viene validato direttamente in fieldOrchestrator.applyFieldSet
        FieldSet defaultFieldSet = null;
        if (ScopeType.TENANT.equals(scopeType)) {
            defaultFieldSet = fieldSetLookup.getFirstDefault(tenant);
            if (defaultFieldSet == null) {
                throw new ApiException("No default FieldSet found for tenant ID " + tenant.getId());
            }
        }

        Set<ItemTypeConfiguration> configurations = new HashSet<>();
        for (ItemTypeConfigurationCreateDto entryDto : dto.itemTypeConfigurations()) {
            ItemTypeConfiguration configuration = workflowOrchestrator.applyWorkflowUpdates(
                    tenant,
                    set,
                    entryDto,
                    null
            );

            fieldOrchestrator.applyFieldSet(
                    tenant,
                    set,
                    configuration,
                    entryDto,
                    defaultFieldSet
            );

            itemTypeConfigurationRepository.save(configuration);
            
            permissionOrchestrator.handlePermissionsForNewConfiguration(configuration);
            
            configurations.add(configuration);
        }

        set.setItemTypeConfigurations(configurations);

        ItemTypeSet saved = itemTypeSetRepository.save(set);
        
        permissionOrchestrator.ensureItemTypeSetPermissions(saved.getId(), tenant);
        
        return dtoMapper.toItemTypeSetViewDto(saved);
    }


    public ItemTypeSetViewDto createGlobal(Tenant tenant, ItemTypeSetCreateDto dto) {
        return this.createItemTypeSet(tenant, dto, ScopeType.TENANT, null);
    }


    public ItemTypeSetViewDto createForProject(Tenant tenant, Long projectId, ItemTypeSetCreateDto dto) {
        // Carico il progetto per tenant e id
        Project project = projectLookup.getById(tenant, projectId);

        ItemTypeSetViewDto itemTypeSet = this.createItemTypeSet(tenant, dto, ScopeType.PROJECT, project);
        return itemTypeSet;
    }



    public ItemTypeSetViewDto updateItemTypeSet(Tenant tenant, Long id, ItemTypeSetUpdateDto dto) {

        ItemTypeSet set = itemTypeSetRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException("ItemTypeSet non trovato: " + id));

        if (set.isDefaultItemTypeSet()) {
            throw new ApiException("Default Item Type Set cannot be edited");
        }

        List<ItemTypeConfigurationCreateDto> dtoConfigurations = dto.itemTypeConfigurations() != null
                ? new ArrayList<>(dto.itemTypeConfigurations())
                : List.of();

        Set<Long> existingConfigurationIds = set.getItemTypeConfigurations().stream()
                .map(ItemTypeConfiguration::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> requestedConfigurationIds = dtoConfigurations.stream()
                .map(ItemTypeConfigurationCreateDto::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> removedConfigurationIds = new HashSet<>(existingConfigurationIds);
        removedConfigurationIds.removeAll(requestedConfigurationIds);

        if (!removedConfigurationIds.isEmpty()) {
            ItemTypeConfigurationRemovalImpactDto impact = analyzeItemTypeConfigurationRemovalImpact(
                    tenant,
                    id,
                    removedConfigurationIds
            );

            if (permissionOrchestrator.hasAssignments(impact)) {
                throw new ApiException("ITEMTYPESET_REMOVAL_IMPACT: rilevate permission con assegnazioni per le configurazioni rimosse");
            }

            // Nessuna assegnazione: rimuovi automaticamente le permission orfane prima di procedere con l'aggiornamento
            permissionOrchestrator.removeOrphanedPermissionsForItemTypeConfigurations(
                    tenant,
                    id,
                    removedConfigurationIds,
                    Collections.emptySet()
            );
        }

        Set<ItemTypeConfiguration> updatedConfigurations = new HashSet<>();

        // Mappa delle configurazioni esistenti per ID per aggiornamento efficiente
        Map<Long, ItemTypeConfiguration> existingConfigsMap = set.getItemTypeConfigurations().stream()
                .collect(java.util.stream.Collectors.toMap(ItemTypeConfiguration::getId, config -> config));

        // Per ItemTypeSet di progetto, non serve il FieldSet di default
        // Il FieldSet viene validato direttamente in fieldOrchestrator.applyFieldSet
        FieldSet defaultFieldSet = null;
        if (ScopeType.TENANT.equals(set.getScope())) {
            defaultFieldSet = fieldSetLookup.getFirstDefault(tenant);
        }

        for (ItemTypeConfigurationCreateDto entryDto : dtoConfigurations) {
            ItemTypeConfiguration existing = entryDto.id() != null ? existingConfigsMap.get(entryDto.id()) : null;

            ItemTypeConfiguration entry = workflowOrchestrator.applyWorkflowUpdates(
                    tenant,
                    set,
                    entryDto,
                    existing
            );

            fieldOrchestrator.applyFieldSet(
                    tenant,
                    set,
                    entry,
                    entryDto,
                    defaultFieldSet
            );

            // Se la configurazione esiste già, verifica se workflow o fieldset sono cambiati
            // e rimuovi le permission obsolete prima di salvare
            if (existing != null) {
                // Carica la configurazione esistente dal database con workflow e fieldset
                // usando JOIN FETCH per evitare problemi di lazy loading
                ItemTypeConfiguration existingConfigLoaded = itemTypeConfigurationRepository
                        .findByIdWithWorkflowAndFieldSet(existing.getId())
                        .orElse(existing);
                
                // Rimuovi le permission obsolete se workflow o fieldset sono cambiati
                permissionOrchestrator.removeObsoletePermissionsForUpdatedConfiguration(
                        tenant,
                        set,
                        existingConfigLoaded,
                        entry
                );
            }

            itemTypeConfigurationRepository.save(entry);
            
            if (existing == null) {
                permissionOrchestrator.handlePermissionsForNewConfiguration(entry);
            }

            updatedConfigurations.add(entry);
        }
        
        // Rimuovi le configurazioni che non sono più nel DTO
        Set<Long> newConfigIds = dtoConfigurations.stream()
                .filter(e -> e.id() != null)
                .map(ItemTypeConfigurationCreateDto::id)
                .collect(java.util.stream.Collectors.toSet());
        
        Set<ItemTypeConfiguration> configsToRemove = set.getItemTypeConfigurations().stream()
                .filter(config -> !newConfigIds.contains(config.getId()))
                .collect(java.util.stream.Collectors.toSet());
        
        // Verifica che dopo la rimozione rimanga almeno una configurazione
        int remainingConfigurations = updatedConfigurations.size();
        if (remainingConfigurations == 0) {
            throw new ApiException("Cannot remove all ItemTypeConfigurations. An ItemTypeSet must have at least one ItemTypeConfiguration.");
        }

        for (ItemTypeConfiguration configToRemove : configsToRemove) {
            itemTypeConfigurationRepository.delete(configToRemove);
        }

        set.getItemTypeConfigurations().clear();
        set.getItemTypeConfigurations().addAll(updatedConfigurations);

        ItemTypeSet updated = itemTypeSetRepository.save(set);
        
        // Crea/aggiorna le permission per l'ItemTypeSet (per assicurarsi che tutte le permission base siano create)
        // Questo è importante quando si aggiungono nuove configurazioni
        // Le permission vengono create automaticamente tramite ItemTypeSetPermissionService
        try {
            permissionOrchestrator.ensureItemTypeSetPermissions(updated.getId(), tenant);
        } catch (Exception e) {
            // Log dell'errore ma non bloccare l'aggiornamento
            log.error("Error creating/updating permissions for ItemTypeSet {}", updated.getId(), e);
        }
        
        return dtoMapper.toItemTypeSetViewDto(updated);
    }





    @Transactional(readOnly = true)
    public ItemTypeSetViewDto getById(Tenant tenant, Long id) {
        return dtoMapper.toItemTypeSetViewDto(itemTypeSetLookup.getById(tenant, id));
    }


    @Transactional(readOnly = true)
    public List<ItemTypeSetViewDto> getAllGlobalItemTypeSets(Tenant tenant) {

        List<ItemTypeSet> sets = itemTypeSetRepository.findAllGlobalWithItemTypeConfigurationsByTenant(tenant);

        return sets.stream()
                .map(dtoMapper::toItemTypeSetViewDto)
                .toList();
    }


    @Transactional(readOnly = true)
    public List<ItemTypeSetViewDto> getAllProjectItemTypeSets(Tenant tenant) {

        List<ItemTypeSet> sets = itemTypeSetRepository.findAllNonGlobalWithItemTypeConfigurationsByTenant(tenant);

        return sets.stream()
                .map(dtoMapper::toItemTypeSetViewDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ItemTypeSetViewDto> getProjectItemTypeSets(Tenant tenant, Long projectId) {
        List<ItemTypeSet> sets = itemTypeSetRepository.findAllByProjectIdAndTenant(projectId, tenant);
        return sets.stream()
                .map(dtoMapper::toItemTypeSetViewDto)
                .toList();
    }

    /**
     * Restituisce gli ItemTypeSet disponibili per un progetto specifico.
     * Per Tenant Admin: tutti gli ITS globali + tutti quelli definiti nel progetto stesso.
     * Per Project Admin: solo quelli definiti nel progetto stesso (chiamare getProjectItemTypeSets).
     */
    @Transactional(readOnly = true)
    public List<ItemTypeSetViewDto> getAvailableItemTypeSetsForProject(Tenant tenant, Long projectId) {
        // Tutti gli ITS globali (scope = TENANT)
        List<ItemTypeSet> globalSets = itemTypeSetRepository.findAllGlobalWithItemTypeConfigurationsByTenant(tenant);
        
        // Tutti gli ITS definiti nel progetto specifico (scope = PROJECT AND project.id = projectId)
        List<ItemTypeSet> projectSets = itemTypeSetRepository.findAllByProjectIdAndTenant(projectId, tenant);
        
        // Combina le due liste
        List<ItemTypeSet> allSets = new ArrayList<>(globalSets);
        allSets.addAll(projectSets);
        
        return allSets.stream()
                .map(dtoMapper::toItemTypeSetViewDto)
                .toList();
    }

    public ItemTypeSetViewDto updateProjectItemTypeSet(Tenant tenant, Long projectId, Long id, ItemTypeSetUpdateDto dto) {
        ItemTypeSet set = itemTypeSetRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException("ItemTypeSet non trovato: " + id));
        
        // Verifica che l'ItemTypeSet appartenga al progetto
        if (set.getProject() == null || !set.getProject().getId().equals(projectId)) {
            throw new ApiException("ItemTypeSet non appartiene al progetto specificato");
        }
        
        return updateItemTypeSet(tenant, id, dto);
    }


    public void deleteItemTypeSet(Tenant tenant, Long id) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException(ITEMTYPESET_NOT_FOUND));

        if (itemTypeSet.isDefaultItemTypeSet()) {
            throw new ApiException("Default Item Type Set cannot be deleted");
        }

        // Verifica se l'ItemTypeSet è usato da almeno un progetto
        long projectCount = projectRepository.countByItemTypeSetIdAndTenantId(id, tenant.getId());
        if (projectCount > 0) {
            throw new ApiException(
                String.format("Cannot delete ItemTypeSet: it is currently used by %d project(s). " +
                    "Please remove the ItemTypeSet from all projects before deleting it.", projectCount)
            );
        }

        // Prima di eliminare l'ITS, elimina tutte le PermissionAssignment associate
        // Questo è necessario perché:
        // 1. Le PermissionAssignment globali (project = null) sono specifiche per una sola permission, quindi vanno eliminate
        // 2. Le PermissionAssignment di progetto (project != null) sono specifiche per un ITS e un progetto, quindi vanno eliminate
        
        // 1. Elimina tutte le PermissionAssignment di progetto per questo ITS
        projectPermissionAssignmentService.deleteByItemTypeSet(id, tenant.getId());
        
        // 2. Per ogni ItemTypeConfiguration nell'ITS, elimina tutte le PermissionAssignment associate
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            // Elimina PermissionAssignment per WorkerPermission
            List<WorkerPermission> workerPermissions = workerPermissionRepository.findAllByItemTypeConfiguration(config);
            for (WorkerPermission perm : workerPermissions) {
                permissionAssignmentService.deleteAssignment("WorkerPermission", perm.getId(), tenant);
            }
            
            // Elimina PermissionAssignment per CreatorPermission
            List<CreatorPermission> creatorPermissions = creatorPermissionRepository.findByItemTypeConfigurationId(config.getId());
            for (CreatorPermission perm : creatorPermissions) {
                permissionAssignmentService.deleteAssignment("CreatorPermission", perm.getId(), tenant);
            }
            
            // Elimina PermissionAssignment per FieldOwnerPermission
            List<FieldOwnerPermission> fieldOwnerPermissions = fieldOwnerPermissionRepository.findAllByItemTypeConfiguration(config);
            for (FieldOwnerPermission perm : fieldOwnerPermissions) {
                permissionAssignmentService.deleteAssignment("FieldOwnerPermission", perm.getId(), tenant);
            }
            
            // Elimina PermissionAssignment per StatusOwnerPermission
            List<StatusOwnerPermission> statusOwnerPermissions = statusOwnerPermissionRepository.findByItemTypeConfigurationIdAndTenant(config.getId(), tenant);
            for (StatusOwnerPermission perm : statusOwnerPermissions) {
                permissionAssignmentService.deleteAssignment("StatusOwnerPermission", perm.getId(), tenant);
            }
            
            // Elimina PermissionAssignment per ExecutorPermission
            List<ExecutorPermission> executorPermissions = executorPermissionRepository.findAllByItemTypeConfiguration(config);
            for (ExecutorPermission perm : executorPermissions) {
                permissionAssignmentService.deleteAssignment("ExecutorPermission", perm.getId(), tenant);
            }
            
            // Elimina PermissionAssignment per FieldStatusPermission
            List<FieldStatusPermission> fieldStatusPermissions = fieldStatusPermissionRepository.findByItemTypeConfigurationIdAndTenant(config.getId(), tenant);
            for (FieldStatusPermission perm : fieldStatusPermissions) {
                permissionAssignmentService.deleteAssignment("FieldStatusPermission", perm.getId(), tenant);
            }
        }
        
        // 3. Elimina anche le grant/ruoli legacy (ItemTypeSetRole) se esistono ancora
        // RIMOSSO: ItemTypeSetRole e ProjectItemTypeSetRoleGrant/ProjectItemTypeSetRoleRole sono state eliminate
        // Le grant sono ora gestite tramite PermissionAssignment
        // TODO: Se necessario, aggiungere logica per eliminare PermissionAssignment quando si elimina ItemTypeSet

        itemTypeSetRepository.deleteByIdAndTenant(id, tenant);
    }

    /**
     * Analizza gli impatti della rimozione di ItemTypeConfiguration da un ItemTypeSet
     */
    @Transactional(readOnly = true)
    public ItemTypeConfigurationRemovalImpactDto analyzeItemTypeConfigurationRemovalImpact(
            Tenant tenant,
            Long itemTypeSetId,
            Set<Long> removedItemTypeConfigurationIds
    ) {
        return permissionOrchestrator.analyzeRemovalImpact(tenant, itemTypeSetId, removedItemTypeConfigurationIds);
    }
    
    /**
     * Rimuove le permissions orfane per le ItemTypeConfiguration rimosse
     * Rimuove tutte le permission associate alle configurazioni rimosse, tranne quelle preservate
     */
    @Transactional
    public void removeOrphanedPermissionsForItemTypeConfigurations(
            Tenant tenant,
            Long itemTypeSetId,
            Set<Long> removedItemTypeConfigurationIds,
            Set<Long> preservedPermissionIds
    ) {
        permissionOrchestrator.removeOrphanedPermissionsForItemTypeConfigurations(
                tenant,
                itemTypeSetId,
                removedItemTypeConfigurationIds,
                preservedPermissionIds
        );
    }

}

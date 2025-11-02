package com.example.demo.service;

import com.example.demo.dto.ItemTypeConfigurationCreateDto;
import com.example.demo.dto.ItemTypeConfigurationRemovalImpactDto;
import com.example.demo.dto.ItemTypeSetCreateDto;
import com.example.demo.dto.ItemTypeSetUpdateDto;
import com.example.demo.dto.ItemTypeSetViewDto;
import com.example.demo.entity.*;
import com.example.demo.enums.ItemTypeSetRoleType;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.factory.FieldSetCloner;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ItemTypeSetService {

    private final ItemTypeSetRepository itemTypeSetRepository;
    private final ProjectRepository projectRepository;

    private final FieldSetRepository fieldSetRepository;
    private final ItemTypeConfigurationRepository itemTypeConfigurationRepository;

    private final FieldSetLookup fieldSetLookup;
    private final ItemTypeLookup itemTypeLookup;
    private final ProjectLookup projectLookup;
    private final ItemTypeSetLookup itemTypeSetLookup;
    private final WorkflowLookup workflowLookup;

    private final DtoMapperFacade dtoMapper;

    private final FieldSetCloner fieldSetCloner;
    private final ItemTypePermissionService itemTypePermissionService;
    private final ItemTypeSetPermissionService itemTypeSetPermissionService;
    
    // Repository per permission
    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final ItemTypeSetRoleRepository itemTypeSetRoleRepository;
    
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

        // Recupera il FieldSet di default del tenant
        FieldSet defaultFieldSet = fieldSetLookup.getFirstDefault(tenant);
        if (defaultFieldSet == null) {
            throw new ApiException("No default FieldSet found for tenant ID " + tenant.getId());
        }

        Set<ItemTypeConfiguration> configurations = new HashSet<>();
        for (ItemTypeConfigurationCreateDto entryDto : dto.itemTypeConfigurations()) {
            ItemType itemType = itemTypeLookup.getById(tenant, entryDto.itemTypeId());
            Workflow workflow = workflowLookup.getByIdEntity(tenant, entryDto.workflowId());
            FieldSet fieldSet = fieldSetLookup.getById(entryDto.fieldSetId(), tenant);

            ItemTypeConfiguration configuration = new ItemTypeConfiguration();
            configuration.setTenant(tenant);
            configuration.setScope(scopeType);
            configuration.setItemType(itemType);
            configuration.setCategory(entryDto.category());
            configuration.setWorkflow(workflow);
            configuration.setFieldSet(fieldSet);
            
            if (!scopeType.equals(ScopeType.TENANT) && project != null) {
                configuration.setProject(project);
            }

            /*
            if (!scopeType.equals(ScopeType.TENANT)) {
                // Clona il FieldSet di default per ogni entry con il FieldSetCloner aggiornato
                FieldSet clonedFieldSet = fieldSetCloner.cloneFieldSet(defaultFieldSet, " (copy for " + itemType.getName() + ")");
                fieldSetRepository.save(clonedFieldSet);

                configuration.setFieldSet(clonedFieldSet);
            }
             */

            itemTypeConfigurationRepository.save(configuration);
            
            // Crea le permissions base per questa configurazione (Creator, Executor, ecc.)
            itemTypePermissionService.createPermissionsForItemTypeConfiguration(configuration);
            
            configurations.add(configuration);
        }

        set.setItemTypeConfigurations(configurations);

        ItemTypeSet saved = itemTypeSetRepository.save(set);
        
        // Crea tutte le permissions per l'ItemTypeSet (WORKER, STATUS_OWNER, FIELD_EDITOR, ecc.)
        itemTypeSetPermissionService.createPermissionsForItemTypeSet(saved.getId(), tenant);
        
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

        Set<ItemTypeConfiguration> updatedConfigurations = new HashSet<>();
        
        // Mappa delle configurazioni esistenti per ID per aggiornamento efficiente
        Map<Long, ItemTypeConfiguration> existingConfigsMap = set.getItemTypeConfigurations().stream()
                .collect(java.util.stream.Collectors.toMap(ItemTypeConfiguration::getId, config -> config));

        // Recupera il FieldSet di default del tenant (per clonazioni)
        FieldSet defaultFieldSet = fieldSetLookup.getFirstDefault(tenant);

        for (ItemTypeConfigurationCreateDto entryDto : dto.itemTypeConfigurations()) {
            ItemType itemType = itemTypeLookup.getById(tenant, entryDto.itemTypeId());
            Workflow workflow = workflowLookup.getByIdEntity(tenant, entryDto.workflowId());
            FieldSet fieldSet = fieldSetLookup.getById(entryDto.fieldSetId(), tenant);

            ItemTypeConfiguration entry;
            
            // Se esiste già una configurazione con questo ID, aggiornala invece di crearne una nuova
            if (entryDto.id() != null && existingConfigsMap.containsKey(entryDto.id())) {
                entry = existingConfigsMap.get(entryDto.id());
                // Aggiorna solo i campi modificabili (non creare una nuova entità)
                entry.setItemType(itemType);
                entry.setCategory(entryDto.category());
                entry.setWorkflow(workflow);
                
                if (!set.getScope().equals(ScopeType.TENANT)) {
                    // Per scope non-TENANT, potrebbe essere necessario clonare
                    // Ma se la configurazione esiste già, probabilmente ha già un FieldSet clonato
                    // Per sicurezza, controlliamo se il FieldSet è diverso
                    if (entry.getFieldSet() == null || !entry.getFieldSet().getId().equals(fieldSet.getId())) {
                        // Clona solo se necessario
                        FieldSet clonedFieldSet = fieldSetCloner.cloneFieldSet(defaultFieldSet, " (copy for " + itemType.getName() + ")");
                        fieldSetRepository.save(clonedFieldSet);
                        entry.setFieldSet(clonedFieldSet);
                    }
                } else {
                    entry.setFieldSet(fieldSet);
                }
            } else {
                // Crea una nuova configurazione solo se non esiste
                entry = new ItemTypeConfiguration();
                entry.setTenant(tenant);
                entry.setScope(set.getScope());
                entry.setItemType(itemType);
                entry.setCategory(entryDto.category());
                entry.setWorkflow(workflow);

                if (!set.getScope().equals(ScopeType.TENANT)) {
                    // Clona il FieldSet associato
                    FieldSet clonedFieldSet = fieldSetCloner.cloneFieldSet(defaultFieldSet, " (copy for " + itemType.getName() + ")");
                    fieldSetRepository.save(clonedFieldSet);
                    entry.setFieldSet(clonedFieldSet);
                } else {
                    entry.setFieldSet(fieldSet);
                }
            }

            // salva subito la configuration (update o create)
            itemTypeConfigurationRepository.save(entry);
            
            // Se è una nuova configurazione (non esisteva già), crea le permission
            boolean isNewConfiguration = entryDto.id() == null || !existingConfigsMap.containsKey(entryDto.id());
            if (isNewConfiguration) {
                // Crea le permissions base per questa nuova configurazione
                itemTypePermissionService.createPermissionsForItemTypeConfiguration(entry);
            }

            updatedConfigurations.add(entry);
        }
        
        // Rimuovi le configurazioni che non sono più nel DTO
        Set<Long> newConfigIds = dto.itemTypeConfigurations().stream()
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
        // Nota: Gli ItemTypeSetRole vengono creati on-the-fly da getPermissionsByItemTypeSet, quindi non serve chiamare createRolesForItemTypeSet qui
        try {
            itemTypeSetPermissionService.createPermissionsForItemTypeSet(updated.getId(), tenant);
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
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByIdAndTenant(itemTypeSetId, tenant)
                .orElseThrow(() -> new ApiException(ITEMTYPESET_NOT_FOUND + ": " + itemTypeSetId));

        // Trova tutte le ItemTypeConfiguration da rimuovere
        List<ItemTypeConfiguration> configsToRemove = itemTypeSet.getItemTypeConfigurations().stream()
                .filter(config -> removedItemTypeConfigurationIds.contains(config.getId()))
                .collect(Collectors.toList());

        if (configsToRemove.isEmpty()) {
            return ItemTypeConfigurationRemovalImpactDto.builder()
                    .itemTypeSetId(itemTypeSetId)
                    .itemTypeSetName(itemTypeSet.getName())
                    .removedItemTypeConfigurationIds(new ArrayList<>(removedItemTypeConfigurationIds))
                    .removedItemTypeConfigurationNames(getItemTypeConfigurationNames(removedItemTypeConfigurationIds, tenant))
                    .affectedItemTypeSets(new ArrayList<>())
                    .fieldOwnerPermissions(new ArrayList<>())
                    .statusOwnerPermissions(new ArrayList<>())
                    .fieldStatusPermissions(new ArrayList<>())
                    .executorPermissions(new ArrayList<>())
                    .itemTypeSetRoles(new ArrayList<>())
                    .totalAffectedItemTypeSets(0)
                    .totalFieldOwnerPermissions(0)
                    .totalStatusOwnerPermissions(0)
                    .totalFieldStatusPermissions(0)
                    .totalExecutorPermissions(0)
                    .totalItemTypeSetRoles(0)
                    .totalGrantAssignments(0)
                    .totalRoleAssignments(0)
                    .build();
        }

        // Analizza le permissions che verranno rimosse
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> fieldOwnerPermissions = 
                analyzeFieldOwnerPermissionImpacts(configsToRemove, itemTypeSet);
        
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> statusOwnerPermissions = 
                analyzeStatusOwnerPermissionImpacts(configsToRemove, itemTypeSet);
        
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> fieldStatusPermissions = 
                analyzeFieldStatusPermissionImpacts(configsToRemove, itemTypeSet);
        
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> executorPermissions = 
                analyzeExecutorPermissionImpacts(configsToRemove, itemTypeSet);
        
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> itemTypeSetRoles = 
                analyzeItemTypeSetRoleImpacts(configsToRemove, itemTypeSet);

        // Calcola statistiche
        int totalGrantAssignments = fieldOwnerPermissions.stream()
                .mapToInt(p -> p.getAssignedGrants() != null ? p.getAssignedGrants().size() : 0)
                .sum() + statusOwnerPermissions.stream()
                .mapToInt(p -> p.getAssignedGrants() != null ? p.getAssignedGrants().size() : 0)
                .sum() + fieldStatusPermissions.stream()
                .mapToInt(p -> p.getAssignedGrants() != null ? p.getAssignedGrants().size() : 0)
                .sum() + executorPermissions.stream()
                .mapToInt(p -> p.getAssignedGrants() != null ? p.getAssignedGrants().size() : 0)
                .sum() + itemTypeSetRoles.stream()
                .mapToInt(p -> p.getAssignedGrants() != null ? p.getAssignedGrants().size() : 0)
                .sum();
        
        int totalRoleAssignments = fieldOwnerPermissions.stream()
                .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                .sum() + statusOwnerPermissions.stream()
                .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                .sum() + fieldStatusPermissions.stream()
                .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                .sum() + executorPermissions.stream()
                .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                .sum() + itemTypeSetRoles.stream()
                .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                .sum();

        // ItemTypeSet coinvolto (sempre l'ItemTypeSet stesso)
        List<ItemTypeConfigurationRemovalImpactDto.ItemTypeSetImpact> affectedItemTypeSets = List.of(
                ItemTypeConfigurationRemovalImpactDto.ItemTypeSetImpact.builder()
                        .itemTypeSetId(itemTypeSet.getId())
                        .itemTypeSetName(itemTypeSet.getName())
                        .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                        .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                        .build()
        );

        return ItemTypeConfigurationRemovalImpactDto.builder()
                .itemTypeSetId(itemTypeSetId)
                .itemTypeSetName(itemTypeSet.getName())
                .removedItemTypeConfigurationIds(new ArrayList<>(removedItemTypeConfigurationIds))
                .removedItemTypeConfigurationNames(getItemTypeConfigurationNames(removedItemTypeConfigurationIds, tenant))
                .affectedItemTypeSets(affectedItemTypeSets)
                .fieldOwnerPermissions(fieldOwnerPermissions)
                .statusOwnerPermissions(statusOwnerPermissions)
                .fieldStatusPermissions(fieldStatusPermissions)
                .executorPermissions(executorPermissions)
                .itemTypeSetRoles(itemTypeSetRoles)
                .totalAffectedItemTypeSets(affectedItemTypeSets.size())
                .totalFieldOwnerPermissions(fieldOwnerPermissions.size())
                .totalStatusOwnerPermissions(statusOwnerPermissions.size())
                .totalFieldStatusPermissions(fieldStatusPermissions.size())
                .totalExecutorPermissions(executorPermissions.size())
                .totalItemTypeSetRoles(itemTypeSetRoles.size())
                .totalGrantAssignments(totalGrantAssignments)
                .totalRoleAssignments(totalRoleAssignments)
                .build();
    }

    // Metodi helper per analizzare i diversi tipi di permission
    private List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> analyzeFieldOwnerPermissionImpacts(
            List<ItemTypeConfiguration> configsToRemove,
            ItemTypeSet itemTypeSet
    ) {
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeConfiguration config : configsToRemove) {
            List<FieldOwnerPermission> permissions = fieldOwnerPermissionRepository.findAllByItemTypeConfiguration(config);
            
            for (FieldOwnerPermission permission : permissions) {
                List<String> assignedRoles = permission.getAssignedRoles() != null
                        ? permission.getAssignedRoles().stream()
                                .map(Role::getName)
                                .collect(Collectors.toList())
                        : new ArrayList<>();
                
                boolean hasAssignments = !assignedRoles.isEmpty();
                
                if (hasAssignments) {
                    impacts.add(ItemTypeConfigurationRemovalImpactDto.PermissionImpact.builder()
                            .permissionId(permission.getId())
                            .permissionType("FIELD_OWNERS")
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .itemTypeConfigurationId(config.getId())
                            .itemTypeName(config.getItemType() != null ? config.getItemType().getName() : null)
                            .itemTypeCategory(config.getCategory() != null ? config.getCategory().toString() : null)
                            .fieldConfigurationId(null) // FieldOwnerPermission è legata a Field, non FieldConfiguration
                            .fieldConfigurationName(permission.getField() != null ? permission.getField().getName() : null)
                            .assignedRoles(assignedRoles)
                            .assignedGrants(new ArrayList<>())
                            .hasAssignments(true)
                            .build());
                }
            }
        }
        
        return impacts;
    }

    private List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> analyzeStatusOwnerPermissionImpacts(
            List<ItemTypeConfiguration> configsToRemove,
            ItemTypeSet itemTypeSet
    ) {
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeConfiguration config : configsToRemove) {
            List<StatusOwnerPermission> permissions = statusOwnerPermissionRepository.findByItemTypeConfigurationId(config.getId());
            
            for (StatusOwnerPermission permission : permissions) {
                List<String> assignedRoles = permission.getAssignedRoles() != null
                        ? permission.getAssignedRoles().stream()
                                .map(Role::getName)
                                .collect(Collectors.toList())
                        : new ArrayList<>();
                
                boolean hasAssignments = !assignedRoles.isEmpty();
                
                if (hasAssignments) {
                    impacts.add(ItemTypeConfigurationRemovalImpactDto.PermissionImpact.builder()
                            .permissionId(permission.getId())
                            .permissionType("STATUS_OWNERS")
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .itemTypeConfigurationId(config.getId())
                            .itemTypeName(config.getItemType() != null ? config.getItemType().getName() : null)
                            .itemTypeCategory(config.getCategory() != null ? config.getCategory().toString() : null)
                            .workflowStatusId(permission.getWorkflowStatus() != null ? permission.getWorkflowStatus().getId() : null)
                            .workflowStatusName(permission.getWorkflowStatus() != null && permission.getWorkflowStatus().getStatus() != null 
                                    ? permission.getWorkflowStatus().getStatus().getName() : null)
                            .assignedRoles(assignedRoles)
                            .assignedGrants(new ArrayList<>())
                            .hasAssignments(true)
                            .build());
                }
            }
        }
        
        return impacts;
    }

    private List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> analyzeFieldStatusPermissionImpacts(
            List<ItemTypeConfiguration> configsToRemove,
            ItemTypeSet itemTypeSet
    ) {
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeConfiguration config : configsToRemove) {
            List<FieldStatusPermission> permissions = fieldStatusPermissionRepository.findByItemTypeConfigurationId(config.getId());
            
            for (FieldStatusPermission permission : permissions) {
                List<String> assignedRoles = permission.getAssignedRoles() != null
                        ? permission.getAssignedRoles().stream()
                                .map(Role::getName)
                                .collect(Collectors.toList())
                        : new ArrayList<>();
                
                boolean hasAssignments = !assignedRoles.isEmpty();
                
                if (hasAssignments) {
                    impacts.add(ItemTypeConfigurationRemovalImpactDto.PermissionImpact.builder()
                            .permissionId(permission.getId())
                            .permissionType(permission.getPermissionType() != null ? permission.getPermissionType().toString() : null)
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .itemTypeConfigurationId(config.getId())
                            .itemTypeName(config.getItemType() != null ? config.getItemType().getName() : null)
                            .itemTypeCategory(config.getCategory() != null ? config.getCategory().toString() : null)
                            .fieldConfigurationId(null) // FieldStatusPermission è legata a Field, non FieldConfiguration
                            .fieldConfigurationName(permission.getField() != null ? permission.getField().getName() : null)
                            .workflowStatusId(permission.getWorkflowStatus() != null ? permission.getWorkflowStatus().getId() : null)
                            .workflowStatusName(permission.getWorkflowStatus() != null && permission.getWorkflowStatus().getStatus() != null
                                    ? permission.getWorkflowStatus().getStatus().getName() : null)
                            .assignedRoles(assignedRoles)
                            .assignedGrants(new ArrayList<>())
                            .hasAssignments(true)
                            .build());
                }
            }
        }
        
        return impacts;
    }

    private List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> analyzeExecutorPermissionImpacts(
            List<ItemTypeConfiguration> configsToRemove,
            ItemTypeSet itemTypeSet
    ) {
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeConfiguration config : configsToRemove) {
            List<ExecutorPermission> permissions = executorPermissionRepository.findAllByItemTypeConfiguration(config);
            
            for (ExecutorPermission permission : permissions) {
                List<String> assignedRoles = permission.getAssignedRoles() != null
                        ? permission.getAssignedRoles().stream()
                                .map(Role::getName)
                                .collect(Collectors.toList())
                        : new ArrayList<>();
                
                boolean hasAssignments = !assignedRoles.isEmpty();
                
                if (hasAssignments && permission.getTransition() != null) {
                    Transition transition = permission.getTransition();
                    impacts.add(ItemTypeConfigurationRemovalImpactDto.PermissionImpact.builder()
                            .permissionId(permission.getId())
                            .permissionType("EXECUTORS")
                            .itemTypeSetId(itemTypeSet.getId())
                            .itemTypeSetName(itemTypeSet.getName())
                            .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                            .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                            .itemTypeConfigurationId(config.getId())
                            .itemTypeName(config.getItemType() != null ? config.getItemType().getName() : null)
                            .itemTypeCategory(config.getCategory() != null ? config.getCategory().toString() : null)
                            .transitionId(transition.getId())
                            .transitionName(transition.getName())
                            .fromStatusName(transition.getFromStatus() != null && transition.getFromStatus().getStatus() != null
                                    ? transition.getFromStatus().getStatus().getName() : null)
                            .toStatusName(transition.getToStatus() != null && transition.getToStatus().getStatus() != null
                                    ? transition.getToStatus().getStatus().getName() : null)
                            .assignedRoles(assignedRoles)
                            .assignedGrants(new ArrayList<>())
                            .hasAssignments(true)
                            .build());
                }
            }
        }
        
        return impacts;
    }

    private List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> analyzeItemTypeSetRoleImpacts(
            List<ItemTypeConfiguration> configsToRemove,
            ItemTypeSet itemTypeSet
    ) {
        List<ItemTypeConfigurationRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeConfiguration config : configsToRemove) {
            // Trova ItemTypeSetRole associati a questa configurazione
            // WORKERS, CREATORS, EXECUTORS, EDITORS, VIEWERS possono essere associati a ItemTypeConfiguration
            List<ItemTypeSetRole> roles = itemTypeSetRoleRepository.findByItemTypeSetIdAndTenantId(itemTypeSet.getId(), itemTypeSet.getTenant().getId());
            
            for (ItemTypeSetRole role : roles) {
                // Verifica se il ruolo è associato a questa configurazione
                boolean isRelatedToConfig = false;
                
                if (role.getRoleType() == ItemTypeSetRoleType.WORKERS && 
                    role.getRelatedEntityType() != null && role.getRelatedEntityType().equals("ItemType") &&
                    role.getRelatedEntityId() != null && config.getItemType() != null &&
                    role.getRelatedEntityId().equals(config.getItemType().getId())) {
                    isRelatedToConfig = true;
                } else if (role.getRoleType() == ItemTypeSetRoleType.CREATORS &&
                          role.getRelatedEntityType() != null && role.getRelatedEntityType().equals("Workflow") &&
                          role.getRelatedEntityId() != null && config.getWorkflow() != null &&
                          role.getRelatedEntityId().equals(config.getWorkflow().getId())) {
                    isRelatedToConfig = true;
                } else if (role.getRoleType() == ItemTypeSetRoleType.EXECUTORS &&
                          role.getRelatedEntityType() != null && role.getRelatedEntityType().equals("Transition") &&
                          config.getWorkflow() != null) {
                    // Per EXECUTORS, devo controllare se la transition appartiene al workflow della config
                    // Questo è più complesso, per ora includiamo tutti gli EXECUTORS del workflow
                    if (role.getRelatedEntityId() != null) {
                        // Controlla se la transition appartiene al workflow
                        // Per semplicità, includiamo se il ruolo è EXECUTORS
                        isRelatedToConfig = true;
                    }
                }
                
                if (isRelatedToConfig) {
                    List<String> assignedRoles = new ArrayList<>();
                    List<String> assignedGrants = new ArrayList<>();
                    boolean hasAssignments = false;
                    
                    if (role.getRoleTemplate() != null) {
                        assignedRoles.add(role.getRoleTemplate().getName());
                        hasAssignments = true;
                    }
                    
                    if (role.getGrant() != null) {
                        assignedGrants.add("Grant diretto");
                        hasAssignments = true;
                    }
                    
                    if (hasAssignments) {
                        impacts.add(ItemTypeConfigurationRemovalImpactDto.PermissionImpact.builder()
                                .permissionId(null) // ItemTypeSetRole non è una Permission nel senso tradizionale
                                .permissionType(role.getRoleType() != null ? role.getRoleType().toString() : null)
                                .itemTypeSetId(itemTypeSet.getId())
                                .itemTypeSetName(itemTypeSet.getName())
                                .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                                .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                                .itemTypeConfigurationId(config.getId())
                                .itemTypeName(config.getItemType() != null ? config.getItemType().getName() : null)
                                .itemTypeCategory(config.getCategory() != null ? config.getCategory().toString() : null)
                                .roleId(role.getRoleTemplate() != null ? role.getRoleTemplate().getId() : null)
                                .roleName(role.getRoleTemplate() != null ? role.getRoleTemplate().getName() : null)
                                .grantId(role.getGrant() != null ? role.getGrant().getId() : null)
                                .grantName(role.getGrant() != null ? "Grant diretto" : null)
                                .assignedRoles(assignedRoles)
                                .assignedGrants(assignedGrants)
                                .hasAssignments(true)
                                .build());
                    }
                }
            }
        }
        
        return impacts;
    }

    private List<String> getItemTypeConfigurationNames(Set<Long> configIds, Tenant tenant) {
        return configIds.stream()
                .map(id -> {
                    try {
                        ItemTypeConfiguration config = itemTypeConfigurationRepository.findById(id)
                                .orElse(null);
                        if (config != null && config.getItemType() != null) {
                            return config.getItemType().getName();
                        }
                        return "Configurazione " + id;
                    } catch (Exception e) {
                        return "Configurazione " + id;
                    }
                })
                .collect(Collectors.toList());
    }

}

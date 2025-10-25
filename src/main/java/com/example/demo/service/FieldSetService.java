package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FieldSetService {

    private final FieldSetRepository fieldSetRepository;
    private final FieldSetEntryRepository fieldSetEntryRepository;
    private final ProjectRepository projectRepository;
    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;

    private final FieldConfigurationLookup fieldConfigurationLookup;
    private final ItemTypeConfigurationLookup itemTypeConfigurationLookup;
    private final FieldSetLookup fieldSetLookup;
    private final ItemTypeSetLookup itemTypeSetLookup;
    private final WorkflowStatusLookup workflowStatusLookup;

    private final DtoMapperFacade dtoMapper;

    private static final String FIELDSET_NOT_FOUND = "FieldSet not found";

    private FieldSet createFieldSet(FieldSetCreateDto dto, Tenant tenant, ScopeType scopeType, Project project) {
        List<Long>  configIds = dto.entries().stream()
                .map(FieldSetEntryCreateDto::fieldConfigurationId)
                .toList();

        // Recupera le FieldConfiguration dal DB
        List<FieldConfiguration> configurations = fieldConfigurationLookup.getAll(configIds, tenant);

        // Mappa ID → FieldConfiguration per accesso veloce
        Map<Long, FieldConfiguration> configMap = configurations.stream()
                .collect(Collectors.toMap(FieldConfiguration::getId, fc -> fc));

        // Crea FieldSet
        FieldSet fieldSet = new FieldSet();
        fieldSet.setName(dto.name());
        fieldSet.setDescription(dto.description());
        fieldSet.setScope(scopeType);
        fieldSet.setTenant(tenant);
        fieldSet.setProject(project);
        fieldSet.setDefaultFieldSet(false);

        List<FieldSetEntry> entries = new ArrayList<>();

        for (FieldSetEntryCreateDto entryDto : dto.entries()) {
            FieldConfiguration config = configMap.get(entryDto.fieldConfigurationId());
            if (config == null) {
                throw new ApiException("Configurazione non trovata: ID " + entryDto.fieldConfigurationId());
            }

            FieldSetEntry entry = new FieldSetEntry();
            entry.setFieldSet(fieldSet);
            entry.setFieldConfiguration(config);
            entry.setOrderIndex(entryDto.orderIndex());

            entries.add(entry);
        }

        fieldSet.setFieldSetEntries(entries);

        return fieldSetRepository.save(fieldSet);
    }

    private void applyFieldSetEntries(Tenant tenant, FieldSet fieldSet, List<FieldSetEntryCreateDto> entryDtos) {
        List<FieldSetEntry> entries = fieldSet.getFieldSetEntries();

        // Rimuovi le entries non più presenti
        entries.removeIf(entry -> entryDtos.stream()
                .noneMatch(dto -> dto.fieldConfigurationId().equals(entry.getFieldConfiguration().getId())));

        // Aggiungi nuove o aggiorna esistenti
        for (int i = 0; i < entryDtos.size(); i++) {
            FieldSetEntryCreateDto dto = entryDtos.get(i);

            Optional<FieldSetEntry> existingEntryOpt = entries.stream()
                    .filter(e -> e.getFieldConfiguration().getId().equals(dto.fieldConfigurationId()))
                    .findFirst();

            if (existingEntryOpt.isPresent()) {
                // Aggiorna orderIndex esistente
                FieldSetEntry existingEntry = existingEntryOpt.get();
                existingEntry.setOrderIndex(i);
            } else {
                // Nuova entry
                FieldConfiguration fc = fieldConfigurationLookup.getById(dto.fieldConfigurationId(), tenant);

                FieldSetEntry newEntry = new FieldSetEntry();
                newEntry.setFieldSet(fieldSet);
                newEntry.setFieldConfiguration(fc);
                newEntry.setOrderIndex(i);

                entries.add(newEntry);
            }
        }
    }



    public FieldSetViewDto createGlobalFieldSet(FieldSetCreateDto dto, Tenant tenant) {
        FieldSet fieldSet = createFieldSet(dto, tenant, ScopeType.TENANT, null);
        return dtoMapper.toFieldSetViewDto(fieldSet);
    }

    public FieldSetViewDto updateFieldSet(Tenant tenant, Long id, FieldSetCreateDto dto) {
        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException(FIELDSET_NOT_FOUND + ": " + id));

        if (fieldSet.isDefaultFieldSet()) throw new ApiException("Default Field Set cannot be edited");

        // Salva i FieldConfiguration esistenti per confronto
        Set<Long> existingFieldConfigIds = fieldSet.getFieldSetEntries().stream()
                .map(entry -> entry.getFieldConfiguration().getId())
                .collect(Collectors.toSet());

        fieldSet.setName(dto.name());
        fieldSet.setDescription(dto.description());

        // ✅ Sostituisce tutta la logica sotto con un metodo chiaro e sicuro
        applyFieldSetEntries(tenant, fieldSet, dto.entries());

        FieldSet saved = fieldSetRepository.save(fieldSet);
        
        // ✅ NUOVO: Gestisci le permissions per le nuove FieldConfiguration
        handlePermissionsForNewFieldConfigurations(tenant, fieldSet, existingFieldConfigIds);
        
        // ✅ NUOVO: Gestisci la rimozione delle permissions per le FieldConfiguration rimosse
        Set<Long> removedFieldConfigIds = existingFieldConfigIds.stream()
                .filter(existingId -> !fieldSet.getFieldSetEntries().stream()
                        .anyMatch(entry -> entry.getFieldConfiguration().getId().equals(existingId)))
                .collect(Collectors.toSet());
        
        if (!removedFieldConfigIds.isEmpty()) {
            // Per ora, rimuoviamo automaticamente le permissions orfane
            // In futuro, potremmo voler mostrare un report prima della rimozione
            removeOrphanedPermissions(tenant, fieldSet.getId(), removedFieldConfigIds);
        }
        
        return dtoMapper.toFieldSetViewDto(saved);
    }


    @Transactional(readOnly = true)
    public List<FieldSetViewDto> getGlobalFieldSets(Tenant tenant) {

        List<FieldSet> sets = fieldSetRepository.findByTenantAndScope(tenant, ScopeType.TENANT);
        return sets.stream().map(dtoMapper::toFieldSetViewDto).toList();
    }


    @Transactional(readOnly = true)
    public FieldSetViewDto getById(Tenant tenant, Long id) {
        return dtoMapper.toFieldSetViewDto(fieldSetLookup.getById(id, tenant));
    }

    public void delete(Tenant tenant, Long id) {

        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException("Field Set not found"));

        if (fieldSet.isDefaultFieldSet()) throw new ApiException("Default Field Set cannot be deleted");
        
        if (!isNotInAnyItemTypeSet(id, tenant)) {
            throw new ApiException("Field Set is used in an ItemTypeSet and cannot be deleted");
        }

        fieldSetRepository.deleteByIdAndTenant(id, tenant);
    }

    @Transactional
    public void reorderEntries(Tenant tenant, Long fieldSetId, List<EntryOrderDto> newOrder) {
        // Recupera il FieldSet con le entries
        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(fieldSetId, tenant)
                .orElseThrow(() -> new ApiException("FieldSet non trovato"));

        if (fieldSet.isDefaultFieldSet()) throw new ApiException("Default Field Set cannot be edited");

        Map<Long, FieldSetEntry> entryMap = fieldSet.getFieldSetEntries()
                .stream()
                .collect(Collectors.toMap(FieldSetEntry::getId, e -> e));

        for (EntryOrderDto dto : newOrder) {
            FieldSetEntry entry = entryMap.get(dto.entryId());
            if (entry == null) {
                throw new ApiException("Entry non trovata: id=" + dto.entryId());
            }
            entry.setOrderIndex(dto.orderIndex());
        }

        // Persisti il cambiamento. Se il repository è un CrudRepository, non serve il save esplicito.
    }

    @Transactional
    public void deleteEntry(Tenant tenant, Long entryId) {
        FieldSetEntry entry = fieldSetEntryRepository.findById(entryId)
                .orElseThrow(() -> new ApiException("FieldSetEntry non trovata: " + entryId));

        if (entry.getFieldSet().isDefaultFieldSet()) throw new ApiException("Default Field Set cannot be edited");

        if (entry.getFieldSet().getTenant().equals(tenant)) {
            // Rimuovi solo l'entry (non toccare la FieldConfiguration)
            fieldSetEntryRepository.delete(entry);
        }
    }


    public FieldSetEntryViewDto addEntry(Tenant tenant, Long fieldSetId, FieldSetEntryCreateDto dto) {
        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(fieldSetId, tenant)
                .orElseThrow(() -> new ApiException("FieldSet non trovato"));

        if (fieldSet.isDefaultFieldSet()) throw new ApiException("Default Field Set cannot be edited");

        FieldConfiguration config = fieldConfigurationLookup.getById(dto.fieldConfigurationId(), tenant);

        // Verifica se è già presente
        boolean alreadyPresent = fieldSet.getFieldSetEntries().stream()
                .anyMatch(e -> e.getFieldConfiguration().getId().equals(config.getId()));

        if (alreadyPresent) {
            throw new ApiException("Questa FieldConfiguration è già presente nel FieldSet");
        }

        FieldSetEntry entry = new FieldSetEntry();
        entry.setFieldSet(fieldSet);
        entry.setFieldConfiguration(config);
        entry.setOrderIndex(dto.orderIndex());

        FieldSetEntry saved = fieldSetEntryRepository.save(entry);

        return dtoMapper.toFieldSetEntryViewDto(saved);
    }


    public boolean isNotInAnyItemTypeSet(Long fieldSetId, Tenant tenant) {
        return !itemTypeConfigurationLookup.isItemTypeConfigurationInAnyFieldSet(fieldSetId, tenant);
    }

    public FieldSet getByFieldSetEntryId(Tenant tenant, Long fieldSetEntryId) {
        FieldSetEntry fieldSetEntry = fieldSetEntryRepository.findById(fieldSetEntryId)
                .orElseThrow(() -> new ApiException("Field Set Entry not found"));
        if (!fieldSetEntry.getFieldSet().getTenant().equals(tenant))
            throw new ApiException("Illegal tenant");
        return fieldSetEntry.getFieldSet();
    }

    public FieldSetViewDto createProjectFieldSet(Tenant tenant, Long projectId, FieldSetCreateDto dto) {
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        FieldSet fieldSet = createFieldSet(dto, tenant, ScopeType.PROJECT, project);
        return dtoMapper.toFieldSetViewDto(fieldSet);
    }

    @Transactional(readOnly = true)
    public List<FieldSetViewDto> getProjectFieldSets(Tenant tenant, Long projectId) {
        List<FieldSet> fieldSets = fieldSetRepository.findByTenantAndProjectIdAndScope(tenant, projectId, ScopeType.PROJECT);
        return dtoMapper.toFieldSetViewDtos(fieldSets);
    }

    /**
     * Gestisce le permissions per le nuove FieldConfiguration aggiunte al FieldSet
     */
    private void handlePermissionsForNewFieldConfigurations(
            Tenant tenant, 
            FieldSet fieldSet, 
            Set<Long> existingFieldConfigIds
    ) {
        // Trova le nuove FieldConfiguration aggiunte
        Set<Long> newFieldConfigIds = fieldSet.getFieldSetEntries().stream()
                .map(entry -> entry.getFieldConfiguration().getId())
                .filter(id -> !existingFieldConfigIds.contains(id))
                .collect(Collectors.toSet());
        
        if (newFieldConfigIds.isEmpty()) {
            return; // Nessuna nuova FieldConfiguration aggiunta
        }
        
        // Trova tutti gli ItemTypeSet che usano questo FieldSet
        List<ItemTypeSet> affectedItemTypeSets = findItemTypeSetsUsingFieldSet(fieldSet.getId(), tenant);
        
        // Per ogni ItemTypeSet, crea le permissions per le nuove FieldConfiguration
        for (ItemTypeSet itemTypeSet : affectedItemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                if (config.getFieldSet().getId().equals(fieldSet.getId())) {
                    // Crea le permissions per le nuove FieldConfiguration
                    createPermissionsForNewFieldConfigurations(config, newFieldConfigIds);
                }
            }
        }
    }
    
    /**
     * Trova tutti gli ItemTypeSet che usano un FieldSet specifico
     */
    private List<ItemTypeSet> findItemTypeSetsUsingFieldSet(Long fieldSetId, Tenant tenant) {
        return itemTypeSetLookup.findByFieldSetId(fieldSetId, tenant);
    }
    
    /**
     * Crea le permissions per le nuove FieldConfiguration in un ItemTypeConfiguration
     */
    private void createPermissionsForNewFieldConfigurations(
            ItemTypeConfiguration config, 
            Set<Long> newFieldConfigIds
    ) {
        for (Long fieldConfigId : newFieldConfigIds) {
            // Crea FIELD_OWNERS permission per la nuova FieldConfiguration
            createFieldOwnerPermission(config, fieldConfigId);
            
            // Crea EDITORS/VIEWERS permissions per le coppie (FieldConfiguration, WorkflowStatus)
            createFieldStatusPermissions(config, fieldConfigId);
        }
    }
    
    /**
     * Crea FIELD_OWNERS permission per una FieldConfiguration
     */
    private void createFieldOwnerPermission(ItemTypeConfiguration config, Long fieldConfigId) {
        // Verifica se la permission esiste già
        FieldOwnerPermission existingPermission = fieldOwnerPermissionRepository
                .findByItemTypeConfigurationAndFieldConfigurationId(config, fieldConfigId);
        
        if (existingPermission == null) {
            // Crea nuova permission
            FieldConfiguration fieldConfig = fieldConfigurationLookup.getById(fieldConfigId, config.getTenant());
            
            FieldOwnerPermission permission = new FieldOwnerPermission();
            permission.setItemTypeConfiguration(config);
            permission.setFieldConfiguration(fieldConfig);
            permission.setAssignedRoles(new HashSet<>());
            
            fieldOwnerPermissionRepository.save(permission);
        }
    }
    
    /**
     * Crea EDITORS/VIEWERS permissions per le coppie (FieldConfiguration, WorkflowStatus)
     */
    private void createFieldStatusPermissions(ItemTypeConfiguration config, Long fieldConfigId) {
        FieldConfiguration fieldConfig = fieldConfigurationLookup.getById(fieldConfigId, config.getTenant());
        
        // Trova tutti gli WorkflowStatus del Workflow associato
        List<WorkflowStatus> workflowStatuses = workflowStatusLookup
                .findAllByWorkflow(config.getWorkflow());
        
        for (WorkflowStatus workflowStatus : workflowStatuses) {
            // Crea EDITORS permission
            createFieldStatusPermission(config, fieldConfig, workflowStatus, FieldStatusPermission.PermissionType.EDITORS);
            
            // Crea VIEWERS permission
            createFieldStatusPermission(config, fieldConfig, workflowStatus, FieldStatusPermission.PermissionType.VIEWERS);
        }
    }
    
    /**
     * Crea una FieldStatusPermission specifica
     */
    private void createFieldStatusPermission(
            ItemTypeConfiguration config,
            FieldConfiguration fieldConfig,
            WorkflowStatus workflowStatus,
            FieldStatusPermission.PermissionType permissionType
    ) {
        // Verifica se la permission esiste già
        FieldStatusPermission existingPermission = fieldStatusPermissionRepository
                .findByItemTypeConfigurationAndFieldConfigurationAndWorkflowStatusAndPermissionType(
                        config, fieldConfig, workflowStatus, permissionType);
        
        if (existingPermission == null) {
            // Crea nuova permission
            FieldStatusPermission permission = new FieldStatusPermission();
            permission.setItemTypeConfiguration(config);
            permission.setFieldConfiguration(fieldConfig);
            permission.setWorkflowStatus(workflowStatus);
            permission.setPermissionType(permissionType);
            permission.setAssignedRoles(new HashSet<>());
            
            fieldStatusPermissionRepository.save(permission);
        }
    }

    /**
     * Analizza gli impatti della rimozione di FieldConfiguration da un FieldSet
     */
    @Transactional(readOnly = true)
    public FieldSetRemovalImpactDto analyzeFieldSetRemovalImpact(
            Tenant tenant, 
            Long fieldSetId, 
            Set<Long> removedFieldConfigIds
    ) {
        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(fieldSetId, tenant)
                .orElseThrow(() -> new ApiException(FIELDSET_NOT_FOUND + ": " + fieldSetId));

        // Trova tutti gli ItemTypeSet che usano questo FieldSet
        List<ItemTypeSet> affectedItemTypeSets = findItemTypeSetsUsingFieldSet(fieldSetId, tenant);
        
        // Analizza le permissions che verranno rimosse
        List<FieldSetRemovalImpactDto.PermissionImpact> fieldOwnerPermissions = 
                analyzeFieldOwnerPermissionImpacts(affectedItemTypeSets, removedFieldConfigIds);
        
        List<FieldSetRemovalImpactDto.PermissionImpact> fieldStatusPermissions = 
                analyzeFieldStatusPermissionImpacts(affectedItemTypeSets, removedFieldConfigIds);
        
        List<FieldSetRemovalImpactDto.PermissionImpact> itemTypeSetRoles = 
                analyzeItemTypeSetRoleImpacts(affectedItemTypeSets, removedFieldConfigIds);
        
        // Calcola statistiche
        int totalGrantAssignments = fieldOwnerPermissions.stream()
                .mapToInt(p -> p.getAssignedGrants() != null ? p.getAssignedGrants().size() : 0)
                .sum() + fieldStatusPermissions.stream()
                .mapToInt(p -> p.getAssignedGrants() != null ? p.getAssignedGrants().size() : 0)
                .sum();
        
        int totalRoleAssignments = fieldOwnerPermissions.stream()
                .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                .sum() + fieldStatusPermissions.stream()
                .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                .sum();

        return FieldSetRemovalImpactDto.builder()
                .fieldSetId(fieldSetId)
                .fieldSetName(fieldSet.getName())
                .removedFieldConfigurationIds(new ArrayList<>(removedFieldConfigIds))
                .removedFieldConfigurationNames(getFieldConfigurationNames(removedFieldConfigIds, tenant))
                .affectedItemTypeSets(mapItemTypeSetImpacts(affectedItemTypeSets))
                .fieldOwnerPermissions(fieldOwnerPermissions)
                .fieldStatusPermissions(fieldStatusPermissions)
                .itemTypeSetRoles(itemTypeSetRoles)
                .totalAffectedItemTypeSets(affectedItemTypeSets.size())
                .totalFieldOwnerPermissions(fieldOwnerPermissions.size())
                .totalFieldStatusPermissions(fieldStatusPermissions.size())
                .totalItemTypeSetRoles(itemTypeSetRoles.size())
                .totalGrantAssignments(totalGrantAssignments)
                .totalRoleAssignments(totalRoleAssignments)
                .build();
    }
    
    /**
     * Rimuove le permissions orfane dopo la conferma dell'utente
     */
    @Transactional
    public void removeOrphanedPermissions(
            Tenant tenant, 
            Long fieldSetId, 
            Set<Long> removedFieldConfigIds
    ) {
        // Trova tutti gli ItemTypeSet che usano questo FieldSet
        List<ItemTypeSet> affectedItemTypeSets = findItemTypeSetsUsingFieldSet(fieldSetId, tenant);
        
        // Rimuovi FieldOwnerPermissions orfane
        removeOrphanedFieldOwnerPermissions(affectedItemTypeSets, removedFieldConfigIds);
        
        // Rimuovi FieldStatusPermissions orfane
        removeOrphanedFieldStatusPermissions(affectedItemTypeSets, removedFieldConfigIds);
        
        // Rimuovi ItemTypeSetRoles orfane
        removeOrphanedItemTypeSetRoles(affectedItemTypeSets, removedFieldConfigIds);
    }
    
    private List<String> getFieldConfigurationNames(Set<Long> fieldConfigIds, Tenant tenant) {
        return fieldConfigIds.stream()
                .map(id -> {
                    try {
                        FieldConfiguration config = fieldConfigurationLookup.getById(id, tenant);
                        return config.getName();
                    } catch (Exception e) {
                        return "Configurazione " + id;
                    }
                })
                .collect(Collectors.toList());
    }
    
    private List<FieldSetRemovalImpactDto.ItemTypeSetImpact> mapItemTypeSetImpacts(List<ItemTypeSet> itemTypeSets) {
        return itemTypeSets.stream()
                .map(its -> FieldSetRemovalImpactDto.ItemTypeSetImpact.builder()
                        .itemTypeSetId(its.getId())
                        .itemTypeSetName(its.getName())
                        .projectId(its.getProject() != null ? its.getProject().getId() : null)
                        .projectName(its.getProject() != null ? its.getProject().getName() : null)
                        .build())
                .collect(Collectors.toList());
    }
    
    private List<FieldSetRemovalImpactDto.PermissionImpact> analyzeFieldOwnerPermissionImpacts(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldConfigIds
    ) {
        List<FieldSetRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                for (Long fieldConfigId : removedFieldConfigIds) {
                    FieldOwnerPermission permission = fieldOwnerPermissionRepository
                            .findByItemTypeConfigurationAndFieldConfigurationId(config, fieldConfigId);
                    
                    if (permission != null) {
                        FieldConfiguration fieldConfig = permission.getFieldConfiguration();
                        List<String> assignedRoles = permission.getAssignedRoles() != null 
                                ? permission.getAssignedRoles().stream()
                                        .map(role -> role.getName())
                                        .collect(Collectors.toList())
                                : new ArrayList<>();
                        
                        impacts.add(FieldSetRemovalImpactDto.PermissionImpact.builder()
                                .permissionId(permission.getId())
                                .permissionType("FIELD_OWNERS")
                                .itemTypeSetId(itemTypeSet.getId())
                                .itemTypeSetName(itemTypeSet.getName())
                                .projectId(itemTypeSet.getProjectsAssociation().isEmpty() ? null : itemTypeSet.getProjectsAssociation().iterator().next().getId())
                                .projectName(itemTypeSet.getProjectsAssociation().isEmpty() ? null : itemTypeSet.getProjectsAssociation().iterator().next().getName())
                                .fieldConfigurationId(fieldConfigId)
                                .fieldConfigurationName(fieldConfig.getName())
                                .assignedRoles(assignedRoles)
                                .hasAssignments(!assignedRoles.isEmpty())
                                .build());
                    }
                }
            }
        }
        
        return impacts;
    }
    
    private List<FieldSetRemovalImpactDto.PermissionImpact> analyzeFieldStatusPermissionImpacts(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldConfigIds
    ) {
        List<FieldSetRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                for (Long fieldConfigId : removedFieldConfigIds) {
                    FieldConfiguration fieldConfig = fieldConfigurationLookup.getById(fieldConfigId, itemTypeSet.getTenant());
                    List<WorkflowStatus> workflowStatuses = workflowStatusLookup.findAllByWorkflow(config.getWorkflow());
                    
                    for (WorkflowStatus workflowStatus : workflowStatuses) {
                        // Controlla EDITORS permission
                        FieldStatusPermission editorsPermission = fieldStatusPermissionRepository
                                .findByItemTypeConfigurationAndFieldConfigurationAndWorkflowStatusAndPermissionType(
                                        config, fieldConfig, workflowStatus, FieldStatusPermission.PermissionType.EDITORS);
                        
                        if (editorsPermission != null) {
                            List<String> assignedRoles = editorsPermission.getAssignedRoles() != null 
                                    ? editorsPermission.getAssignedRoles().stream()
                                            .map(role -> role.getName())
                                            .collect(Collectors.toList())
                                    : new ArrayList<>();
                            
                            impacts.add(FieldSetRemovalImpactDto.PermissionImpact.builder()
                                    .permissionId(editorsPermission.getId())
                                    .permissionType("EDITORS")
                                    .itemTypeSetId(itemTypeSet.getId())
                                    .itemTypeSetName(itemTypeSet.getName())
                                    .projectId(itemTypeSet.getProjectsAssociation().isEmpty() ? null : itemTypeSet.getProjectsAssociation().iterator().next().getId())
                                    .projectName(itemTypeSet.getProjectsAssociation().isEmpty() ? null : itemTypeSet.getProjectsAssociation().iterator().next().getName())
                                    .fieldConfigurationId(fieldConfigId)
                                    .fieldConfigurationName(fieldConfig.getName())
                                    .workflowStatusId(workflowStatus.getId())
                                    .workflowStatusName(workflowStatus.getStatus().getName())
                                    .assignedRoles(assignedRoles)
                                    .hasAssignments(!assignedRoles.isEmpty())
                                    .build());
                        }
                        
                        // Controlla VIEWERS permission
                        FieldStatusPermission viewersPermission = fieldStatusPermissionRepository
                                .findByItemTypeConfigurationAndFieldConfigurationAndWorkflowStatusAndPermissionType(
                                        config, fieldConfig, workflowStatus, FieldStatusPermission.PermissionType.VIEWERS);
                        
                        if (viewersPermission != null) {
                            List<String> assignedRoles = viewersPermission.getAssignedRoles() != null 
                                    ? viewersPermission.getAssignedRoles().stream()
                                            .map(role -> role.getName())
                                            .collect(Collectors.toList())
                                    : new ArrayList<>();
                            
                            impacts.add(FieldSetRemovalImpactDto.PermissionImpact.builder()
                                    .permissionId(viewersPermission.getId())
                                    .permissionType("VIEWERS")
                                    .itemTypeSetId(itemTypeSet.getId())
                                    .itemTypeSetName(itemTypeSet.getName())
                                    .projectId(itemTypeSet.getProjectsAssociation().isEmpty() ? null : itemTypeSet.getProjectsAssociation().iterator().next().getId())
                                    .projectName(itemTypeSet.getProjectsAssociation().isEmpty() ? null : itemTypeSet.getProjectsAssociation().iterator().next().getName())
                                    .fieldConfigurationId(fieldConfigId)
                                    .fieldConfigurationName(fieldConfig.getName())
                                    .workflowStatusId(workflowStatus.getId())
                                    .workflowStatusName(workflowStatus.getStatus().getName())
                                    .assignedRoles(assignedRoles)
                                    .hasAssignments(!assignedRoles.isEmpty())
                                    .build());
                        }
                    }
                }
            }
        }
        
        return impacts;
    }
    
    private List<FieldSetRemovalImpactDto.PermissionImpact> analyzeItemTypeSetRoleImpacts(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldConfigIds
    ) {
        // TODO: Implementare analisi ItemTypeSetRole se necessario
        // Per ora ritorniamo lista vuota
        return new ArrayList<>();
    }
    
    private void removeOrphanedFieldOwnerPermissions(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldConfigIds
    ) {
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                for (Long fieldConfigId : removedFieldConfigIds) {
                    FieldOwnerPermission permission = fieldOwnerPermissionRepository
                            .findByItemTypeConfigurationAndFieldConfigurationId(config, fieldConfigId);
                    
                    if (permission != null) {
                        fieldOwnerPermissionRepository.delete(permission);
                    }
                }
            }
        }
    }
    
    private void removeOrphanedFieldStatusPermissions(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldConfigIds
    ) {
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                for (Long fieldConfigId : removedFieldConfigIds) {
                    FieldConfiguration fieldConfig = fieldConfigurationLookup.getById(fieldConfigId, itemTypeSet.getTenant());
                    List<WorkflowStatus> workflowStatuses = workflowStatusLookup.findAllByWorkflow(config.getWorkflow());
                    
                    for (WorkflowStatus workflowStatus : workflowStatuses) {
                        // Rimuovi EDITORS permission
                        FieldStatusPermission editorsPermission = fieldStatusPermissionRepository
                                .findByItemTypeConfigurationAndFieldConfigurationAndWorkflowStatusAndPermissionType(
                                        config, fieldConfig, workflowStatus, FieldStatusPermission.PermissionType.EDITORS);
                        
                        if (editorsPermission != null) {
                            fieldStatusPermissionRepository.delete(editorsPermission);
                        }
                        
                        // Rimuovi VIEWERS permission
                        FieldStatusPermission viewersPermission = fieldStatusPermissionRepository
                                .findByItemTypeConfigurationAndFieldConfigurationAndWorkflowStatusAndPermissionType(
                                        config, fieldConfig, workflowStatus, FieldStatusPermission.PermissionType.VIEWERS);
                        
                        if (viewersPermission != null) {
                            fieldStatusPermissionRepository.delete(viewersPermission);
                        }
                    }
                }
            }
        }
    }
    
    private void removeOrphanedItemTypeSetRoles(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldConfigIds
    ) {
        // TODO: Implementare rimozione ItemTypeSetRoles se necessario
    }















}

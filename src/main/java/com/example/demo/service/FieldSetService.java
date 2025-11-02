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
    private final ProjectItemTypeSetRoleGrantRepository projectItemTypeSetRoleGrantRepository;
    private final ItemTypeSetRoleRepository itemTypeSetRoleRepository;

    private final FieldConfigurationLookup fieldConfigurationLookup;
    private final FieldLookup fieldLookup;
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

        // Salva i Field IDs esistenti per confronto (non FieldConfiguration!)
        Set<Long> existingFieldIds = fieldSet.getFieldSetEntries().stream()
                .map(entry -> entry.getFieldConfiguration().getField().getId())
                .collect(Collectors.toSet());

        fieldSet.setName(dto.name());
        fieldSet.setDescription(dto.description());

        applyFieldSetEntries(tenant, fieldSet, dto.entries());

        FieldSet saved = fieldSetRepository.save(fieldSet);
        
        // ✅ IMPORTANTE: Calcola i Field IDs FINALI (dopo le modifiche) basandosi sulle FieldConfiguration del DTO
        // Questo ci permette di identificare correttamente quali Field sono veramente nuovi
        Set<Long> finalFieldIds = dto.entries().stream()
                .map(entryDto -> {
                    try {
                        FieldConfiguration config = fieldConfigurationLookup.getById(entryDto.fieldConfigurationId(), tenant);
                        return config.getField().getId();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(fieldId -> fieldId != null)
                .collect(Collectors.toSet());
        
        // ✅ Identifica i Field veramente nuovi (non presenti nel FieldSet originale)
        // IMPORTANTE: Un Field è "nuovo" solo se NON era presente nel FieldSet originale
        // Se cambi FieldConfiguration per lo stesso Field, il Field non è "nuovo", quindi non creare permission
        Set<Long> trulyNewFieldIds = finalFieldIds.stream()
                .filter(fieldId -> !existingFieldIds.contains(fieldId))
                .collect(Collectors.toSet());
        
        // ✅ Identifica i Field completamente rimossi (non presenti nel FieldSet finale)
        Set<Long> removedFieldIds = existingFieldIds.stream()
                .filter(fieldId -> !finalFieldIds.contains(fieldId))
                .collect(Collectors.toSet());
        
        // ✅ Gestisci le permissions solo per Field VERAMENTE nuovi (non per cambio FieldConfiguration)
        if (!trulyNewFieldIds.isEmpty()) {
            handlePermissionsForNewFields(tenant, saved, trulyNewFieldIds);
        }
        
        // ✅ IMPORTANTE: Non rimuovere le permission qui!
        // La rimozione delle permissions viene gestita SOLO tramite il report di impatto
        // quando l'utente conferma esplicitamente la rimozione di un Field completamente rimosso
        // Se un Field rimane (anche con FieldConfiguration diversa), le permission devono rimanere intatte
        
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
     * Gestisce le permissions per i nuovi Field aggiunti al FieldSet
     * IMPORTANTE: Le permission sono ora associate al Field, non alla FieldConfiguration
     */
    private void handlePermissionsForNewFields(
            Tenant tenant, 
            FieldSet fieldSet, 
            Set<Long> newFieldIds
    ) {
        if (newFieldIds.isEmpty()) {
            return; // Nessun nuovo Field aggiunto
        }
        
        // Trova tutti gli ItemTypeSet che usano questo FieldSet
        List<ItemTypeSet> affectedItemTypeSets = findItemTypeSetsUsingFieldSet(fieldSet.getId(), tenant);
        
        // Per ogni ItemTypeSet, crea le permissions per i nuovi Field
        for (ItemTypeSet itemTypeSet : affectedItemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                if (config.getFieldSet().getId().equals(fieldSet.getId())) {
                    // Crea le permissions per i nuovi Field
                    createPermissionsForNewFields(config, newFieldIds);
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
     * Crea le permissions per i nuovi Field in un ItemTypeConfiguration
     * IMPORTANTE: Le permission sono associate al Field, non alla FieldConfiguration
     */
    private void createPermissionsForNewFields(
            ItemTypeConfiguration config, 
            Set<Long> newFieldIds
    ) {
        for (Long fieldId : newFieldIds) {
            // Crea FIELD_OWNERS permission per il nuovo Field
            createFieldOwnerPermission(config, fieldId);
            
            // Crea EDITORS/VIEWERS permissions per le coppie (Field, WorkflowStatus)
            createFieldStatusPermissions(config, fieldId);
        }
    }
    
    /**
     * Crea FIELD_OWNERS permission per un Field
     */
    private void createFieldOwnerPermission(ItemTypeConfiguration config, Long fieldId) {
        // Verifica se la permission esiste già
        FieldOwnerPermission existingPermission = fieldOwnerPermissionRepository
                .findByItemTypeConfigurationAndFieldId(config, fieldId);
        
        if (existingPermission == null) {
            // Crea nuova permission
            Field field = fieldLookup.getById(fieldId, config.getTenant());
            
            FieldOwnerPermission permission = new FieldOwnerPermission();
            permission.setItemTypeConfiguration(config);
            permission.setField(field);
            permission.setAssignedRoles(new HashSet<>());
            
            fieldOwnerPermissionRepository.save(permission);
        }
    }
    
    /**
     * Crea EDITORS/VIEWERS permissions per le coppie (Field, WorkflowStatus)
     */
    private void createFieldStatusPermissions(ItemTypeConfiguration config, Long fieldId) {
        Field field = fieldLookup.getById(fieldId, config.getTenant());
        
        // Trova tutti gli WorkflowStatus del Workflow associato
        List<WorkflowStatus> workflowStatuses = workflowStatusLookup
                .findAllByWorkflow(config.getWorkflow());
        
        for (WorkflowStatus workflowStatus : workflowStatuses) {
            // Crea EDITORS permission
            createFieldStatusPermission(config, field, workflowStatus, FieldStatusPermission.PermissionType.EDITORS);
            
            // Crea VIEWERS permission
            createFieldStatusPermission(config, field, workflowStatus, FieldStatusPermission.PermissionType.VIEWERS);
        }
    }
    
    /**
     * Crea una FieldStatusPermission specifica
     */
    private void createFieldStatusPermission(
            ItemTypeConfiguration config,
            Field field,
            WorkflowStatus workflowStatus,
            FieldStatusPermission.PermissionType permissionType
    ) {
        // Verifica se la permission esiste già
        FieldStatusPermission existingPermission = fieldStatusPermissionRepository
                .findByItemTypeConfigurationAndFieldAndWorkflowStatusAndPermissionType(
                        config, field, workflowStatus, permissionType);
        
        if (existingPermission == null) {
            // Crea nuova permission
            FieldStatusPermission permission = new FieldStatusPermission();
            permission.setItemTypeConfiguration(config);
            permission.setField(field);
            permission.setWorkflowStatus(workflowStatus);
            permission.setPermissionType(permissionType);
            permission.setAssignedRoles(new HashSet<>());
            
            fieldStatusPermissionRepository.save(permission);
        }
    }

    /**
     * Analizza gli impatti della rimozione di Field da un FieldSet
     * IMPORTANTE: Le permission sono associate al Field, non alla FieldConfiguration.
     * Solo i Field completamente rimossi (nessuna FieldConfiguration per quel Field rimane) avranno impatto.
     * 
     * @param addedFieldConfigIds Le nuove configurazioni che verranno aggiunte (per calcolare correttamente quali Field rimarranno)
     */
    @Transactional(readOnly = true)
    public FieldSetRemovalImpactDto analyzeFieldSetRemovalImpact(
            Tenant tenant, 
            Long fieldSetId, 
            Set<Long> removedFieldConfigIds,
            Set<Long> addedFieldConfigIds
    ) {
        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(fieldSetId, tenant)
                .orElseThrow(() -> new ApiException(FIELDSET_NOT_FOUND + ": " + fieldSetId));

        // Converti removedFieldConfigIds in removedFieldIds
        // IMPORTANTE: Solo i Field completamente rimossi (nessuna FieldConfiguration per quel Field rimane) hanno impatto
        // IMPORTANTE: Deve considerare anche le nuove configurazioni che verranno aggiunte
        
        // 1. Trova tutte le configurazioni attuali nel FieldSet
        Set<Long> currentConfigIds = fieldSet.getFieldSetEntries().stream()
                .map(entry -> entry.getFieldConfiguration().getId())
                .collect(Collectors.toSet());
        
        // 2. Calcola le configurazioni che RIMARRANNO dopo la rimozione E l'aggiunta
        // Rimuovi quelle rimosse e aggiungi quelle nuove
        Set<Long> remainingConfigIds = new HashSet<>(currentConfigIds);
        remainingConfigIds.removeAll(removedFieldConfigIds); // Rimuovi quelle rimosse
        if (addedFieldConfigIds != null) {
            remainingConfigIds.addAll(addedFieldConfigIds); // Aggiungi quelle nuove
        }
        
        // 3. Calcola i Field IDs dalle configurazioni che rimarranno
        Set<Long> remainingFieldIds = remainingConfigIds.stream()
                .map(configId -> {
                    try {
                        FieldConfiguration config = fieldConfigurationLookup.getById(configId, tenant);
                        return config.getField().getId();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(fieldId -> fieldId != null)
                .collect(Collectors.toSet());
        
        // 4. Per ogni configurazione rimossa, verifica se il suo Field è completamente rimosso
        Set<Long> removedFieldIds = new HashSet<>();
        for (Long fieldConfigId : removedFieldConfigIds) {
            FieldConfiguration removedConfig = fieldConfigurationLookup.getById(fieldConfigId, tenant);
            Long fieldId = removedConfig.getField().getId();
            
            // Il Field è completamente rimosso solo se nessuna FieldConfiguration per quel Field rimane nel FieldSet
            if (!remainingFieldIds.contains(fieldId)) {
                removedFieldIds.add(fieldId);
            }
        }

        // Se non ci sono Field completamente rimossi, ritorna un report vuoto
        if (removedFieldIds.isEmpty()) {
            return FieldSetRemovalImpactDto.builder()
                    .fieldSetId(fieldSetId)
                    .fieldSetName(fieldSet.getName())
                    .removedFieldConfigurationIds(new ArrayList<>(removedFieldConfigIds))
                    .removedFieldConfigurationNames(getFieldConfigurationNames(removedFieldConfigIds, tenant))
                    .affectedItemTypeSets(new ArrayList<>())
                    .fieldOwnerPermissions(new ArrayList<>())
                    .fieldStatusPermissions(new ArrayList<>())
                    .itemTypeSetRoles(new ArrayList<>())
                    .totalAffectedItemTypeSets(0)
                    .totalFieldOwnerPermissions(0)
                    .totalFieldStatusPermissions(0)
                    .totalItemTypeSetRoles(0)
                    .totalGrantAssignments(0)
                    .totalRoleAssignments(0)
                    .build();
        }

        // Trova tutti gli ItemTypeSet che usano questo FieldSet
        List<ItemTypeSet> allItemTypeSetsUsingFieldSet = findItemTypeSetsUsingFieldSet(fieldSetId, tenant);
        
        // Analizza le permissions che verranno rimosse (solo per Field completamente rimossi)
        // IMPORTANTE: Passa anche remainingFieldIds per calcolare canBePreserved
        List<FieldSetRemovalImpactDto.PermissionImpact> fieldOwnerPermissions = 
                analyzeFieldOwnerPermissionImpacts(allItemTypeSetsUsingFieldSet, removedFieldIds, remainingFieldIds, fieldSet);
        
        List<FieldSetRemovalImpactDto.PermissionImpact> fieldStatusPermissions = 
                analyzeFieldStatusPermissionImpacts(allItemTypeSetsUsingFieldSet, removedFieldIds, remainingFieldIds, tenant);
        
        List<FieldSetRemovalImpactDto.PermissionImpact> itemTypeSetRoles = 
                analyzeItemTypeSetRoleImpacts(allItemTypeSetsUsingFieldSet, removedFieldIds);
        
        // Calcola solo gli ItemTypeSet che hanno effettivamente impatti (permissions con ruoli assegnati)
        Set<Long> itemTypeSetIdsWithImpact = new HashSet<>();
        fieldOwnerPermissions.forEach(p -> itemTypeSetIdsWithImpact.add(p.getItemTypeSetId()));
        fieldStatusPermissions.forEach(p -> itemTypeSetIdsWithImpact.add(p.getItemTypeSetId()));
        itemTypeSetRoles.forEach(p -> itemTypeSetIdsWithImpact.add(p.getItemTypeSetId()));
        
        List<ItemTypeSet> affectedItemTypeSets = allItemTypeSetsUsingFieldSet.stream()
                .filter(its -> itemTypeSetIdsWithImpact.contains(its.getId()))
                .collect(Collectors.toList());
        
        // Calcola statistiche
        // IMPORTANTE: Conta le grant dagli ItemTypeSetRole associati SOLO alle permission che verranno effettivamente rimosse
        // Usa un Set per evitare di contare la stessa grant più volte (se la stessa grant è associata a più ruoli)
        Set<Long> countedGrantIds = new HashSet<>();
        
        // IMPORTANTE: Per evitare di contare grant duplicate, usiamo solo le permission effettivamente impattate
        // In un FieldSet non ci possono essere più FieldConfiguration per lo stesso Field
        // Quindi per ogni FieldOwnerPermission che verrà rimossa, trova l'ItemTypeSetRole corrispondente
        
        // Crea una mappa: FieldConfiguration ID -> Field ID (per il FieldSet modificato)
        Set<Long> fieldSetConfigIds = fieldSet.getFieldSetEntries().stream()
                .map(e -> e.getFieldConfiguration().getId())
                .collect(Collectors.toSet());
        
        java.util.Map<Long, Long> configIdToFieldId = new java.util.HashMap<>();
        for (FieldSetEntry entry : fieldSet.getFieldSetEntries()) {
            configIdToFieldId.put(entry.getFieldConfiguration().getId(), entry.getFieldConfiguration().getField().getId());
        }
        
        // Per ogni FieldOwnerPermission che verrà rimossa
        Set<Long> processedItemTypeSetAndFieldPairs = new HashSet<>(); // Per evitare duplicati
        
        for (FieldSetRemovalImpactDto.PermissionImpact perm : fieldOwnerPermissions) {
            Long itemTypeSetId = perm.getItemTypeSetId();
            Long fieldId = perm.getFieldId();
            
            // Crea una chiave unica per (ItemTypeSet, Field) per evitare di contare la stessa grant due volte
            // Se lo stesso ItemTypeSet ha più ItemTypeConfiguration con lo stesso Field, conta solo una volta
            long uniqueKey = itemTypeSetId * 1000000L + fieldId; // Combinazione unica
            if (processedItemTypeSetAndFieldPairs.contains(uniqueKey)) {
                continue; // Già processato
            }
            processedItemTypeSetAndFieldPairs.add(uniqueKey);
            
            // Trova la FieldConfiguration corrispondente a questo Field nel FieldSet modificato
            // (dato che in un FieldSet non ci possono essere più FieldConfiguration per lo stesso Field)
            Long matchingConfigId = null;
            for (Long configId : fieldSetConfigIds) {
                if (configIdToFieldId.containsKey(configId) && configIdToFieldId.get(configId).equals(fieldId)) {
                    matchingConfigId = configId;
                    break;
                }
            }
            
            if (matchingConfigId != null && removedFieldConfigIds.contains(matchingConfigId)) {
                // Trova l'ItemTypeSetRole FIELD_OWNERS per questa FieldConfiguration
                Optional<com.example.demo.entity.ItemTypeSetRole> fieldOwnerRole = itemTypeSetRoleRepository
                        .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                                itemTypeSetId, 
                                "FieldConfiguration", 
                                matchingConfigId, 
                                com.example.demo.enums.ItemTypeSetRoleType.FIELD_OWNERS, 
                                tenant.getId());
                
                if (fieldOwnerRole.isPresent() && fieldOwnerRole.get().getGrant() != null) {
                    Long grantId = fieldOwnerRole.get().getGrant().getId();
                    countedGrantIds.add(grantId);
                }
            }
        }
        
        // Per FieldStatusPermissions (EDITORS/VIEWERS)
        for (FieldSetRemovalImpactDto.PermissionImpact perm : fieldStatusPermissions) {
            if (perm.getFieldConfigurationId() != null) {
                // Verifica che questa FieldConfiguration appartenga al FieldSet modificato E che verrà rimossa
                if (fieldSetConfigIds.contains(perm.getFieldConfigurationId()) 
                        && removedFieldConfigIds.contains(perm.getFieldConfigurationId())) {
                    com.example.demo.enums.ItemTypeSetRoleType roleType = "EDITORS".equals(perm.getPermissionType()) 
                            ? com.example.demo.enums.ItemTypeSetRoleType.EDITORS 
                            : com.example.demo.enums.ItemTypeSetRoleType.VIEWERS;
                    
                    List<com.example.demo.entity.ItemTypeSetRole> editorsViewersRoles = itemTypeSetRoleRepository
                            .findByItemTypeSetIdAndRoleTypeAndTenantId(perm.getItemTypeSetId(), roleType, tenant.getId());
                    
                    for (com.example.demo.entity.ItemTypeSetRole role : editorsViewersRoles) {
                        if (role.getSecondaryEntityId() != null 
                                && role.getSecondaryEntityId().equals(perm.getFieldConfigurationId())
                                && "FieldConfiguration".equals(role.getSecondaryEntityType())) {
                            if (role.getGrant() != null) {
                                Long grantId = role.getGrant().getId();
                                countedGrantIds.add(grantId);
                            }
                        }
                    }
                }
            }
        }
        
        int totalGrantAssignments = countedGrantIds.size();
        
        int totalRoleAssignments = fieldOwnerPermissions.stream()
                .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                .sum() + fieldStatusPermissions.stream()
                .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                .sum();

        // Mappa gli ItemTypeSet con informazioni aggregate (incluso conteggio grant di progetto)
        List<FieldSetRemovalImpactDto.ItemTypeSetImpact> mappedItemTypeSets = 
                mapItemTypeSetImpactsWithAggregates(
                        affectedItemTypeSets,
                        fieldOwnerPermissions,
                        fieldStatusPermissions,
                        itemTypeSetRoles,
                        tenant
                );

        return FieldSetRemovalImpactDto.builder()
                .fieldSetId(fieldSetId)
                .fieldSetName(fieldSet.getName())
                .removedFieldConfigurationIds(new ArrayList<>(removedFieldConfigIds))
                .removedFieldConfigurationNames(getFieldConfigurationNames(removedFieldConfigIds, tenant))
                .affectedItemTypeSets(mappedItemTypeSets)
                .fieldOwnerPermissions(fieldOwnerPermissions)
                .fieldStatusPermissions(fieldStatusPermissions)
                .itemTypeSetRoles(itemTypeSetRoles)
                .totalAffectedItemTypeSets(affectedItemTypeSets.size()) // Solo quelli con impatti effettivi
                .totalFieldOwnerPermissions(fieldOwnerPermissions.size())
                .totalFieldStatusPermissions(fieldStatusPermissions.size())
                .totalItemTypeSetRoles(itemTypeSetRoles.size())
                .totalGrantAssignments(totalGrantAssignments)
                .totalRoleAssignments(totalRoleAssignments)
                .build();
    }
    
    /**
     * Rimuove le permissions orfane dopo la conferma dell'utente
     * IMPORTANTE: Converti removedFieldConfigIds in removedFieldIds e rimuovi solo per Field completamente rimossi
     * 
     * @param addedFieldConfigIds Le nuove configurazioni che verranno aggiunte (per calcolare correttamente quali Field rimarranno)
     */
    @Transactional
    public void removeOrphanedPermissions(
            Tenant tenant, 
            Long fieldSetId, 
            Set<Long> removedFieldConfigIds,
            Set<Long> addedFieldConfigIds,
            Set<Long> preservedPermissionIds
    ) {
        FieldSet fieldSet = fieldSetRepository.findByIdAndTenant(fieldSetId, tenant)
                .orElseThrow(() -> new ApiException(FIELDSET_NOT_FOUND + ": " + fieldSetId));
        
        // Converti removedFieldConfigIds in removedFieldIds (solo Field completamente rimossi)
        // IMPORTANTE: Calcoliamo i Field rimasti DOPO la rimozione E l'aggiunta, non quelli attuali
        // IMPORTANTE: Deve considerare anche le nuove configurazioni che verranno aggiunte
        
        // 1. Trova tutte le configurazioni attuali nel FieldSet
        Set<Long> currentConfigIds = fieldSet.getFieldSetEntries().stream()
                .map(entry -> entry.getFieldConfiguration().getId())
                .collect(Collectors.toSet());
        
        // 2. Calcola le configurazioni che RIMARRANNO dopo la rimozione E l'aggiunta
        // Rimuovi quelle rimosse e aggiungi quelle nuove
        Set<Long> remainingConfigIds = new HashSet<>(currentConfigIds);
        remainingConfigIds.removeAll(removedFieldConfigIds); // Rimuovi quelle rimosse
        if (addedFieldConfigIds != null) {
            remainingConfigIds.addAll(addedFieldConfigIds); // Aggiungi quelle nuove
        }
        
        // 3. Calcola i Field IDs dalle configurazioni che rimarranno
        Set<Long> remainingFieldIds = remainingConfigIds.stream()
                .map(configId -> {
                    try {
                        FieldConfiguration config = fieldConfigurationLookup.getById(configId, tenant);
                        return config.getField().getId();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(fieldId -> fieldId != null)
                .collect(Collectors.toSet());
        
        // 4. Per ogni configurazione rimossa, verifica se il suo Field è completamente rimosso
        Set<Long> removedFieldIds = new HashSet<>();
        for (Long fieldConfigId : removedFieldConfigIds) {
            FieldConfiguration removedConfig = fieldConfigurationLookup.getById(fieldConfigId, tenant);
            Long fieldId = removedConfig.getField().getId();
            
            // Il Field è completamente rimosso solo se nessuna FieldConfiguration per quel Field rimane nel FieldSet
            if (!remainingFieldIds.contains(fieldId)) {
                removedFieldIds.add(fieldId);
            }
        }
        
        if (removedFieldIds.isEmpty()) {
            return; // Nessun Field completamente rimosso, non rimuovere permission
        }
        
        // Trova tutti gli ItemTypeSet che usano questo FieldSet
        List<ItemTypeSet> affectedItemTypeSets = findItemTypeSetsUsingFieldSet(fieldSetId, tenant);
        
        // Rimuovi FieldOwnerPermissions orfane (escludendo quelle preservate)
        removeOrphanedFieldOwnerPermissions(affectedItemTypeSets, removedFieldIds, preservedPermissionIds);
        
        // Rimuovi FieldStatusPermissions orfane (escludendo quelle preservate)
        removeOrphanedFieldStatusPermissions(affectedItemTypeSets, removedFieldIds, preservedPermissionIds);
        
        // Rimuovi ItemTypeSetRoles orfane (escludendo quelle preservate)
        removeOrphanedItemTypeSetRoles(affectedItemTypeSets, removedFieldIds, preservedPermissionIds);
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
    
    /**
     * Mappa gli ItemTypeSet con informazioni aggregate (permission, ruoli, grant globali e di progetto)
     */
    private List<FieldSetRemovalImpactDto.ItemTypeSetImpact> mapItemTypeSetImpactsWithAggregates(
            List<ItemTypeSet> itemTypeSets,
            List<FieldSetRemovalImpactDto.PermissionImpact> fieldOwnerPermissions,
            List<FieldSetRemovalImpactDto.PermissionImpact> fieldStatusPermissions,
            List<FieldSetRemovalImpactDto.PermissionImpact> itemTypeSetRoles,
            Tenant tenant
    ) {
        return itemTypeSets.stream()
                .map(its -> {
                    Long itemTypeSetId = its.getId();
                    
                    // Filtra permission per questo ItemTypeSet
                    List<FieldSetRemovalImpactDto.PermissionImpact> itsPermissions = new ArrayList<>();
                    itsPermissions.addAll(fieldOwnerPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList()));
                    itsPermissions.addAll(fieldStatusPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList()));
                    itsPermissions.addAll(itemTypeSetRoles.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList()));
                    
                    // Calcola totali
                    int totalPermissions = itsPermissions.size();
                    int totalRoleAssignments = itsPermissions.stream()
                            .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                            .sum();
                    int totalGlobalGrants = itsPermissions.stream()
                            .mapToInt(p -> p.getAssignedGrants() != null ? p.getAssignedGrants().size() : 0)
                            .sum();
                    
                    // Calcola grant di progetto per questo ItemTypeSet
                    // IMPORTANTE: Conta solo le grant di progetto per i ruoli associati alle permission che verranno rimosse
                    // Raccogli tutti i roleId dalle permission che verranno rimosse
                    Set<Long> relevantRoleIds = new HashSet<>();
                    for (FieldSetRemovalImpactDto.PermissionImpact perm : itsPermissions) {
                        if (perm.getRoleId() != null) {
                            relevantRoleIds.add(perm.getRoleId());
                        }
                        // Per permission che hanno projectGrants, aggiungi anche quei roleId
                        if (perm.getProjectGrants() != null) {
                            for (FieldSetRemovalImpactDto.ProjectGrantInfo pg : perm.getProjectGrants()) {
                                if (pg.getRoleId() != null) {
                                    relevantRoleIds.add(pg.getRoleId());
                                }
                            }
                        }
                    }
                    
                    // Trova tutti i progetti che usano questo ItemTypeSet
                    Set<Project> projects = its.getProjectsAssociation();
                    List<FieldSetRemovalImpactDto.ProjectImpact> projectImpacts = new ArrayList<>();
                    int totalProjectGrants = 0;
                    
                    for (Project project : projects) {
                        // Conta SOLO le grant di progetto per i ruoli rilevanti (associati alle permission rimosse)
                        List<com.example.demo.entity.ProjectItemTypeSetRoleGrant> allProjectGrants = 
                                projectItemTypeSetRoleGrantRepository.findByItemTypeSetIdAndProjectIdAndTenantId(
                                        itemTypeSetId, project.getId(), tenant.getId());
                        
                        // Filtra solo quelle per ruoli rilevanti
                        long projectGrantsCount = allProjectGrants.stream()
                                .filter(pg -> pg.getItemTypeSetRole() != null 
                                        && relevantRoleIds.contains(pg.getItemTypeSetRole().getId()))
                                .count();
                        
                        totalProjectGrants += (int) projectGrantsCount;
                        
                        if (projectGrantsCount > 0) {
                            projectImpacts.add(FieldSetRemovalImpactDto.ProjectImpact.builder()
                                    .projectId(project.getId())
                                    .projectName(project.getName())
                                    .projectGrantsCount((int) projectGrantsCount)
                                    .build());
                        }
                    }
                    
                    return FieldSetRemovalImpactDto.ItemTypeSetImpact.builder()
                            .itemTypeSetId(itemTypeSetId)
                            .itemTypeSetName(its.getName())
                            .projectId(its.getProject() != null ? its.getProject().getId() : null)
                            .projectName(its.getProject() != null ? its.getProject().getName() : null)
                            .totalPermissions(totalPermissions)
                            .totalRoleAssignments(totalRoleAssignments)
                            .totalGlobalGrants(totalGlobalGrants)
                            .totalProjectGrants(totalProjectGrants)
                            .projectImpacts(projectImpacts)
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Analizza gli impatti delle FieldOwnerPermission per Field rimossi
     * @param remainingFieldIds I Field che rimarranno nel FieldSet finale (per calcolare canBePreserved)
     * @param fieldSet Il FieldSet modificato (per trovare la FieldConfiguration corretta)
     */
    private List<FieldSetRemovalImpactDto.PermissionImpact> analyzeFieldOwnerPermissionImpacts(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldIds,
            Set<Long> remainingFieldIds,
            FieldSet fieldSet
    ) {
        List<FieldSetRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                for (Long fieldId : removedFieldIds) {
                    FieldOwnerPermission permission = fieldOwnerPermissionRepository
                            .findByItemTypeConfigurationAndFieldId(config, fieldId);
                    
                    if (permission != null) {
                        Field field = permission.getField();
                        // IMPORTANTE: assignedRoles è già caricato grazie al JOIN FETCH nella query
                        List<String> assignedRoles = permission.getAssignedRoles() != null 
                                ? permission.getAssignedRoles().stream()
                                        .map(role -> role.getName())
                                        .collect(Collectors.toList())
                                : new ArrayList<>();
                        
                        
                        // Solo se ha ruoli assegnati
                        if (!assignedRoles.isEmpty()) {
                            // IMPORTANTE: Trova la FieldConfiguration corretta dal FieldSet modificato
                            // Non una "di esempio", ma quella specifica che verrà rimossa
                            FieldConfiguration targetConfigTemp = null;
                            for (FieldSetEntry entry : fieldSet.getFieldSetEntries()) {
                                if (entry.getFieldConfiguration().getField().getId().equals(fieldId)) {
                                    targetConfigTemp = entry.getFieldConfiguration();
                                    break; // In un FieldSet non ci possono essere più FieldConfiguration per lo stesso Field
                                }
                            }
                            
                            // Se non troviamo la FieldConfiguration nel FieldSet modificato, prendiamo una di esempio
                            // (potrebbe succedere se il FieldSet è già stato modificato prima)
                            if (targetConfigTemp == null) {
                                targetConfigTemp = fieldConfigurationLookup.getAllByField(fieldId, itemTypeSet.getTenant())
                                        .stream()
                                        .findFirst()
                                        .orElse(null);
                            }
                            
                            // Variabile finale per uso nei lambda
                            final FieldConfiguration targetConfig = targetConfigTemp;
                            
                            // Calcola canBePreserved: true se il Field rimane nel FieldSet finale
                            // NOTA: Se canBePreserved = true, la permission verrebbe mantenuta automaticamente,
                            // quindi non dovrebbe apparire qui. Ma per coerenza con altri report, lo includiamo comunque.
                            boolean canBePreserved = remainingFieldIds.contains(fieldId);
                            boolean defaultPreserve = canBePreserved && !assignedRoles.isEmpty();
                            
                            // Trova l'ItemTypeSetRole FIELD_OWNERS corrispondente e le grant di progetto
                            Long roleId = null;
                            Long globalGrantId = null;
                            String globalGrantName = null;
                            List<FieldSetRemovalImpactDto.ProjectGrantInfo> projectGrantsList = new ArrayList<>();
                            
                            // Cerca TUTTI gli ItemTypeSetRole FIELD_OWNERS per questa FieldConfiguration
                            // IMPORTANTE: Possono esserci multipli se lo stesso ItemTypeSet ha più ItemTypeConfiguration
                            // che usano la stessa FieldConfiguration (stesso FieldSet)
                            if (targetConfig != null) {
                                // Trova tutti i ruoli FIELD_OWNERS per questa FieldConfiguration nello stesso ItemTypeSet
                                List<ItemTypeSetRole> fieldOwnerRoles = itemTypeSetRoleRepository
                                        .findByItemTypeSetIdAndRoleTypeAndTenantId(
                                                itemTypeSet.getId(),
                                                com.example.demo.enums.ItemTypeSetRoleType.FIELD_OWNERS,
                                                itemTypeSet.getTenant().getId())
                                        .stream()
                                        .filter(role -> role.getRelatedEntityId() != null 
                                                && role.getRelatedEntityId().equals(targetConfig.getId())
                                                && "FieldConfiguration".equals(role.getRelatedEntityType()))
                                        .collect(Collectors.toList());
                                
                                // Per ogni ruolo trovato, raccogli grant globali e di progetto
                                Set<Long> processedProjectIds = new HashSet<>(); // Per evitare duplicati
                                
                                for (ItemTypeSetRole role : fieldOwnerRoles) {
                                    // Usa il primo ruolo per roleId e grant globale (se non già impostati)
                                    if (roleId == null) {
                                        roleId = role.getId();
                                        
                                        // Grant globale
                                        if (role.getGrant() != null) {
                                            globalGrantId = role.getGrant().getId();
                                            globalGrantName = role.getGrant().getRole() != null 
                                                    ? role.getGrant().getRole().getName() 
                                                    : "Grant globale";
                                        }
                                    }
                                    
                                    // Grant di progetto: trova TUTTE le grant di progetto per questo ruolo
                                    List<ProjectItemTypeSetRoleGrant> allProjectGrantsForRole = 
                                            projectItemTypeSetRoleGrantRepository.findByItemTypeSetRoleIdAndTenantId(
                                                    role.getId(),
                                                    itemTypeSet.getTenant().getId());
                                    
                                    // Per ogni grant di progetto trovata, aggiungi il progetto alla lista (evitando duplicati)
                                    for (ProjectItemTypeSetRoleGrant projectGrant : allProjectGrantsForRole) {
                                        if (projectGrant.getProject() != null && !processedProjectIds.contains(projectGrant.getProject().getId())) {
                                            processedProjectIds.add(projectGrant.getProject().getId());
                                            projectGrantsList.add(FieldSetRemovalImpactDto.ProjectGrantInfo.builder()
                                                    .projectId(projectGrant.getProject().getId())
                                                    .projectName(projectGrant.getProject().getName())
                                                    .roleId(role.getId())
                                                    .build());
                                        }
                                    }
                                }
                            }
                            
                            impacts.add(FieldSetRemovalImpactDto.PermissionImpact.builder()
                                    .permissionId(permission.getId())
                                    .permissionType("FIELD_OWNERS")
                                    .itemTypeSetId(itemTypeSet.getId())
                                    .itemTypeSetName(itemTypeSet.getName())
                                    .projectId(itemTypeSet.getProjectsAssociation().isEmpty() ? null : itemTypeSet.getProjectsAssociation().iterator().next().getId())
                                    .projectName(itemTypeSet.getProjectsAssociation().isEmpty() ? null : itemTypeSet.getProjectsAssociation().iterator().next().getName())
                                    .fieldConfigurationId(targetConfig != null ? targetConfig.getId() : null)
                                    .fieldConfigurationName(targetConfig != null ? targetConfig.getName() : field.getName())
                                    .fieldId(fieldId)
                                    .fieldName(field.getName())
                                    .matchingFieldId(canBePreserved ? fieldId : null)
                                    .matchingFieldName(canBePreserved ? field.getName() : null)
                                    .assignedRoles(assignedRoles)
                                    .hasAssignments(true)
                                    .canBePreserved(canBePreserved)
                                    .defaultPreserve(defaultPreserve)
                                    .roleId(roleId)
                                    .grantId(globalGrantId)
                                    .grantName(globalGrantName)
                                    .projectGrants(projectGrantsList)
                                    .build());
                        }
                    }
                }
            }
        }
        
        return impacts;
    }
    
    /**
     * Analizza gli impatti delle FieldStatusPermission per Field rimossi
     * @param remainingFieldIds I Field che rimarranno nel FieldSet finale (per calcolare canBePreserved)
     * @param tenant Il tenant (per ottenere informazioni sugli Status)
     */
    private List<FieldSetRemovalImpactDto.PermissionImpact> analyzeFieldStatusPermissionImpacts(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldIds,
            Set<Long> remainingFieldIds,
            Tenant tenant
    ) {
        List<FieldSetRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                for (Long fieldId : removedFieldIds) {
                    Field field = fieldLookup.getById(fieldId, itemTypeSet.getTenant());
                    List<WorkflowStatus> workflowStatuses = workflowStatusLookup.findAllByWorkflow(config.getWorkflow());
                    
                    for (WorkflowStatus workflowStatus : workflowStatuses) {
                        // Controlla EDITORS permission
                        FieldStatusPermission editorsPermission = fieldStatusPermissionRepository
                                .findByItemTypeConfigurationAndFieldAndWorkflowStatusAndPermissionType(
                                        config, field, workflowStatus, FieldStatusPermission.PermissionType.EDITORS);
                        
                        if (editorsPermission != null) {
                            // IMPORTANTE: assignedRoles è già caricato grazie al JOIN FETCH nella query
                            List<String> assignedRoles = editorsPermission.getAssignedRoles() != null 
                                    ? editorsPermission.getAssignedRoles().stream()
                                            .map(role -> role.getName())
                                            .collect(Collectors.toList())
                                    : new ArrayList<>();
                            
                            
                            // Solo se ha ruoli assegnati
                            if (!assignedRoles.isEmpty()) {
                                // Per il DTO, manteniamo fieldConfigurationId/Name per retrocompatibilità
                                FieldConfiguration exampleConfig = fieldConfigurationLookup.getAllByField(fieldId, itemTypeSet.getTenant())
                                        .stream()
                                        .findFirst()
                                        .orElse(null);
                                
                                // Calcola canBePreserved: true se Field E Status rimangono
                                // Per FieldStatusPermission: Status rimane sempre se modifichi solo FieldSet (non Workflow)
                                // Quindi canBePreserved = true se il Field rimane
                                Status status = workflowStatus.getStatus();
                                boolean fieldRemains = remainingFieldIds.contains(fieldId);
                                // Status rimane sempre quando modifichi solo FieldSet (non Workflow)
                                boolean statusRemains = true; // Se modifichi solo FieldSet, il Workflow non cambia
                                boolean canBePreserved = fieldRemains && statusRemains;
                                boolean defaultPreserve = canBePreserved && !assignedRoles.isEmpty();
                                
                                impacts.add(FieldSetRemovalImpactDto.PermissionImpact.builder()
                                        .permissionId(editorsPermission.getId())
                                        .permissionType("EDITORS")
                                        .itemTypeSetId(itemTypeSet.getId())
                                        .itemTypeSetName(itemTypeSet.getName())
                                        .projectId(itemTypeSet.getProjectsAssociation().isEmpty() ? null : itemTypeSet.getProjectsAssociation().iterator().next().getId())
                                        .projectName(itemTypeSet.getProjectsAssociation().isEmpty() ? null : itemTypeSet.getProjectsAssociation().iterator().next().getName())
                                        .fieldConfigurationId(exampleConfig != null ? exampleConfig.getId() : null)
                                        .fieldConfigurationName(exampleConfig != null ? exampleConfig.getName() : field.getName())
                                        .workflowStatusId(workflowStatus.getId())
                                        .workflowStatusName(workflowStatus.getStatus().getName())
                                        .fieldId(fieldId)
                                        .fieldName(field.getName())
                                        .statusId(status != null ? status.getId() : null)
                                        .statusName(status != null ? status.getName() : null)
                                        .matchingFieldId(canBePreserved && fieldRemains ? fieldId : null)
                                        .matchingFieldName(canBePreserved && fieldRemains ? field.getName() : null)
                                        .matchingStatusId(canBePreserved && statusRemains ? (status != null ? status.getId() : null) : null)
                                        .matchingStatusName(canBePreserved && statusRemains ? (status != null ? status.getName() : null) : null)
                                        .assignedRoles(assignedRoles)
                                        .hasAssignments(true)
                                        .canBePreserved(canBePreserved)
                                        .defaultPreserve(defaultPreserve)
                                        .build());
                            }
                        }
                        
                        // Controlla VIEWERS permission
                        FieldStatusPermission viewersPermission = fieldStatusPermissionRepository
                                .findByItemTypeConfigurationAndFieldAndWorkflowStatusAndPermissionType(
                                        config, field, workflowStatus, FieldStatusPermission.PermissionType.VIEWERS);
                        
                        if (viewersPermission != null) {
                            // IMPORTANTE: assignedRoles è già caricato grazie al JOIN FETCH nella query
                            List<String> assignedRoles = viewersPermission.getAssignedRoles() != null 
                                    ? viewersPermission.getAssignedRoles().stream()
                                            .map(role -> role.getName())
                                            .collect(Collectors.toList())
                                    : new ArrayList<>();
                            
                            
                            // Solo se ha ruoli assegnati
                            if (!assignedRoles.isEmpty()) {
                                // Per il DTO, manteniamo fieldConfigurationId/Name per retrocompatibilità
                                FieldConfiguration exampleConfig = fieldConfigurationLookup.getAllByField(fieldId, itemTypeSet.getTenant())
                                        .stream()
                                        .findFirst()
                                        .orElse(null);
                                
                                // Calcola canBePreserved: true se Field E Status rimangono
                                Status status = workflowStatus.getStatus();
                                boolean fieldRemains = remainingFieldIds.contains(fieldId);
                                boolean statusRemains = true; // Se modifichi solo FieldSet, il Workflow non cambia
                                boolean canBePreserved = fieldRemains && statusRemains;
                                boolean defaultPreserve = canBePreserved && !assignedRoles.isEmpty();
                                
                                impacts.add(FieldSetRemovalImpactDto.PermissionImpact.builder()
                                        .permissionId(viewersPermission.getId())
                                        .permissionType("VIEWERS")
                                        .itemTypeSetId(itemTypeSet.getId())
                                        .itemTypeSetName(itemTypeSet.getName())
                                        .projectId(itemTypeSet.getProjectsAssociation().isEmpty() ? null : itemTypeSet.getProjectsAssociation().iterator().next().getId())
                                        .projectName(itemTypeSet.getProjectsAssociation().isEmpty() ? null : itemTypeSet.getProjectsAssociation().iterator().next().getName())
                                        .fieldConfigurationId(exampleConfig != null ? exampleConfig.getId() : null)
                                        .fieldConfigurationName(exampleConfig != null ? exampleConfig.getName() : field.getName())
                                        .workflowStatusId(workflowStatus.getId())
                                        .workflowStatusName(workflowStatus.getStatus().getName())
                                        .fieldId(fieldId)
                                        .fieldName(field.getName())
                                        .statusId(status != null ? status.getId() : null)
                                        .statusName(status != null ? status.getName() : null)
                                        .matchingFieldId(canBePreserved && fieldRemains ? fieldId : null)
                                        .matchingFieldName(canBePreserved && fieldRemains ? field.getName() : null)
                                        .matchingStatusId(canBePreserved && statusRemains ? (status != null ? status.getId() : null) : null)
                                        .matchingStatusName(canBePreserved && statusRemains ? (status != null ? status.getName() : null) : null)
                                        .assignedRoles(assignedRoles)
                                        .hasAssignments(true)
                                        .canBePreserved(canBePreserved)
                                        .defaultPreserve(defaultPreserve)
                                        .build());
                            }
                        }
                    }
                }
            }
        }
        
        return impacts;
    }
    
    private List<FieldSetRemovalImpactDto.PermissionImpact> analyzeItemTypeSetRoleImpacts(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldIds
    ) {
        return new ArrayList<>();
    }
    
    /**
     * Rimuove le FieldOwnerPermission orfane per Field rimossi
     */
    private void removeOrphanedFieldOwnerPermissions(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldIds,
            Set<Long> preservedPermissionIds
    ) {
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                for (Long fieldId : removedFieldIds) {
                    FieldOwnerPermission permission = fieldOwnerPermissionRepository
                            .findByItemTypeConfigurationAndFieldId(config, fieldId);
                    
                    if (permission != null && !preservedPermissionIds.contains(permission.getId())) {
                        fieldOwnerPermissionRepository.delete(permission);
                    }
                }
            }
        }
    }
    
    /**
     * Rimuove le FieldStatusPermission orfane per Field rimossi
     */
    private void removeOrphanedFieldStatusPermissions(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldIds,
            Set<Long> preservedPermissionIds
    ) {
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                for (Long fieldId : removedFieldIds) {
                    Field field = fieldLookup.getById(fieldId, itemTypeSet.getTenant());
                    List<WorkflowStatus> workflowStatuses = workflowStatusLookup.findAllByWorkflow(config.getWorkflow());
                    
                    for (WorkflowStatus workflowStatus : workflowStatuses) {
                        // Rimuovi EDITORS permission (se non preservata)
                        FieldStatusPermission editorsPermission = fieldStatusPermissionRepository
                                .findByItemTypeConfigurationAndFieldAndWorkflowStatusAndPermissionType(
                                        config, field, workflowStatus, FieldStatusPermission.PermissionType.EDITORS);
                        
                        if (editorsPermission != null && !preservedPermissionIds.contains(editorsPermission.getId())) {
                            fieldStatusPermissionRepository.delete(editorsPermission);
                        }
                        
                        // Rimuovi VIEWERS permission (se non preservata)
                        FieldStatusPermission viewersPermission = fieldStatusPermissionRepository
                                .findByItemTypeConfigurationAndFieldAndWorkflowStatusAndPermissionType(
                                        config, field, workflowStatus, FieldStatusPermission.PermissionType.VIEWERS);
                        
                        if (viewersPermission != null && !preservedPermissionIds.contains(viewersPermission.getId())) {
                            fieldStatusPermissionRepository.delete(viewersPermission);
                        }
                    }
                }
            }
        }
    }
    
    private void removeOrphanedItemTypeSetRoles(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldIds,
            Set<Long> preservedPermissionIds
    ) {
        // Method kept for consistency but not implemented as ItemTypeSetRoles are handled separately
    }















}

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

        // ✅ Sostituisce tutta la logica sotto con un metodo chiaro e sicuro
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
        
        // DEBUG: Log per capire cosa succede durante il salvataggio
        System.out.println("=== FieldSet Update ===");
        System.out.println("FieldSet ID: " + id);
        System.out.println("Existing Field IDs: " + existingFieldIds);
        System.out.println("Final Field IDs: " + finalFieldIds);
        System.out.println("Truly New Field IDs: " + trulyNewFieldIds);
        System.out.println("Removed Field IDs: " + removedFieldIds);
        
        // ✅ Gestisci le permissions solo per Field VERAMENTE nuovi (non per cambio FieldConfiguration)
        if (!trulyNewFieldIds.isEmpty()) {
            System.out.println("Creating permissions for new Fields: " + trulyNewFieldIds);
            handlePermissionsForNewFields(tenant, saved, trulyNewFieldIds);
        } else {
            System.out.println("No new Fields - permissions will not be created or removed");
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
            System.out.println("Created new FieldOwnerPermission for Field ID " + fieldId + " in ItemTypeConfiguration " + config.getId());
        } else {
            System.out.println("FieldOwnerPermission already exists for Field ID " + fieldId + " in ItemTypeConfiguration " + config.getId() + 
                              " - preserving existing permission with " + (existingPermission.getAssignedRoles() != null ? existingPermission.getAssignedRoles().size() : 0) + " roles");
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
            System.out.println("Created new FieldStatusPermission for Field ID " + field.getId() + 
                              " Status " + workflowStatus.getId() + " Type " + permissionType + 
                              " in ItemTypeConfiguration " + config.getId());
        } else {
            System.out.println("FieldStatusPermission already exists for Field ID " + field.getId() + 
                              " Status " + workflowStatus.getId() + " Type " + permissionType + 
                              " in ItemTypeConfiguration " + config.getId() + 
                              " - preserving existing permission with " + (existingPermission.getAssignedRoles() != null ? existingPermission.getAssignedRoles().size() : 0) + " roles");
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

        // DEBUG: Log per capire cosa succede
        System.out.println("=== FieldSet Removal Impact Analysis ===");
        System.out.println("FieldSet ID: " + fieldSetId);
        System.out.println("Removed FieldConfig IDs: " + removedFieldConfigIds);
        System.out.println("Added FieldConfig IDs: " + (addedFieldConfigIds != null ? addedFieldConfigIds : "null"));
        System.out.println("Remaining Config IDs: " + remainingConfigIds);
        System.out.println("Remaining Field IDs: " + remainingFieldIds);
        System.out.println("Removed Field IDs: " + removedFieldIds);

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
        List<FieldSetRemovalImpactDto.PermissionImpact> fieldOwnerPermissions = 
                analyzeFieldOwnerPermissionImpacts(allItemTypeSetsUsingFieldSet, removedFieldIds);
        
        List<FieldSetRemovalImpactDto.PermissionImpact> fieldStatusPermissions = 
                analyzeFieldStatusPermissionImpacts(allItemTypeSetsUsingFieldSet, removedFieldIds);
        
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
            Set<Long> addedFieldConfigIds
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
        
        // Rimuovi FieldOwnerPermissions orfane
        removeOrphanedFieldOwnerPermissions(affectedItemTypeSets, removedFieldIds);
        
        // Rimuovi FieldStatusPermissions orfane
        removeOrphanedFieldStatusPermissions(affectedItemTypeSets, removedFieldIds);
        
        // Rimuovi ItemTypeSetRoles orfane
        removeOrphanedItemTypeSetRoles(affectedItemTypeSets, removedFieldIds);
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
     * Analizza gli impatti delle FieldOwnerPermission per Field rimossi
     */
    private List<FieldSetRemovalImpactDto.PermissionImpact> analyzeFieldOwnerPermissionImpacts(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldIds
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
                        
                        // DEBUG: Log per capire se i ruoli vengono trovati
                        System.out.println("Found FieldOwnerPermission for Field ID " + fieldId + 
                                          " in ItemTypeSet " + itemTypeSet.getId() + 
                                          " - Assigned roles: " + assignedRoles.size());
                        
                        // Solo se ha ruoli assegnati
                        if (!assignedRoles.isEmpty()) {
                            // Per il DTO, manteniamo fieldConfigurationId/Name per retrocompatibilità
                            // ma in realtà ora è un Field - prendiamo una FieldConfiguration di esempio per quel Field
                            FieldConfiguration exampleConfig = fieldConfigurationLookup.getAllByField(fieldId, itemTypeSet.getTenant())
                                    .stream()
                                    .findFirst()
                                    .orElse(null);
                            
                            impacts.add(FieldSetRemovalImpactDto.PermissionImpact.builder()
                                    .permissionId(permission.getId())
                                    .permissionType("FIELD_OWNERS")
                                    .itemTypeSetId(itemTypeSet.getId())
                                    .itemTypeSetName(itemTypeSet.getName())
                                    .projectId(itemTypeSet.getProjectsAssociation().isEmpty() ? null : itemTypeSet.getProjectsAssociation().iterator().next().getId())
                                    .projectName(itemTypeSet.getProjectsAssociation().isEmpty() ? null : itemTypeSet.getProjectsAssociation().iterator().next().getName())
                                    .fieldConfigurationId(exampleConfig != null ? exampleConfig.getId() : null)
                                    .fieldConfigurationName(exampleConfig != null ? exampleConfig.getName() : field.getName())
                                    .assignedRoles(assignedRoles)
                                    .hasAssignments(true)
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
     */
    private List<FieldSetRemovalImpactDto.PermissionImpact> analyzeFieldStatusPermissionImpacts(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldIds
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
                            
                            // DEBUG: Log per capire se i ruoli vengono trovati
                            System.out.println("Found FieldStatusPermission (EDITORS) for Field ID " + fieldId + 
                                              " in ItemTypeSet " + itemTypeSet.getId() + 
                                              " Status " + workflowStatus.getId() +
                                              " - Assigned roles: " + assignedRoles.size());
                            
                            // Solo se ha ruoli assegnati
                            if (!assignedRoles.isEmpty()) {
                                // Per il DTO, manteniamo fieldConfigurationId/Name per retrocompatibilità
                                FieldConfiguration exampleConfig = fieldConfigurationLookup.getAllByField(fieldId, itemTypeSet.getTenant())
                                        .stream()
                                        .findFirst()
                                        .orElse(null);
                                
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
                                        .assignedRoles(assignedRoles)
                                        .hasAssignments(true)
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
                            
                            // DEBUG: Log per capire se i ruoli vengono trovati
                            System.out.println("Found FieldStatusPermission (VIEWERS) for Field ID " + fieldId + 
                                              " in ItemTypeSet " + itemTypeSet.getId() + 
                                              " Status " + workflowStatus.getId() +
                                              " - Assigned roles: " + assignedRoles.size());
                            
                            // Solo se ha ruoli assegnati
                            if (!assignedRoles.isEmpty()) {
                                // Per il DTO, manteniamo fieldConfigurationId/Name per retrocompatibilità
                                FieldConfiguration exampleConfig = fieldConfigurationLookup.getAllByField(fieldId, itemTypeSet.getTenant())
                                        .stream()
                                        .findFirst()
                                        .orElse(null);
                                
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
                                        .assignedRoles(assignedRoles)
                                        .hasAssignments(true)
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
        // TODO: Implementare analisi ItemTypeSetRole se necessario
        // Per ora ritorniamo lista vuota
        return new ArrayList<>();
    }
    
    /**
     * Rimuove le FieldOwnerPermission orfane per Field rimossi
     */
    private void removeOrphanedFieldOwnerPermissions(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedFieldIds
    ) {
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                for (Long fieldId : removedFieldIds) {
                    FieldOwnerPermission permission = fieldOwnerPermissionRepository
                            .findByItemTypeConfigurationAndFieldId(config, fieldId);
                    
                    if (permission != null) {
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
            Set<Long> removedFieldIds
    ) {
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                for (Long fieldId : removedFieldIds) {
                    Field field = fieldLookup.getById(fieldId, itemTypeSet.getTenant());
                    List<WorkflowStatus> workflowStatuses = workflowStatusLookup.findAllByWorkflow(config.getWorkflow());
                    
                    for (WorkflowStatus workflowStatus : workflowStatuses) {
                        // Rimuovi EDITORS permission
                        FieldStatusPermission editorsPermission = fieldStatusPermissionRepository
                                .findByItemTypeConfigurationAndFieldAndWorkflowStatusAndPermissionType(
                                        config, field, workflowStatus, FieldStatusPermission.PermissionType.EDITORS);
                        
                        if (editorsPermission != null) {
                            fieldStatusPermissionRepository.delete(editorsPermission);
                        }
                        
                        // Rimuovi VIEWERS permission
                        FieldStatusPermission viewersPermission = fieldStatusPermissionRepository
                                .findByItemTypeConfigurationAndFieldAndWorkflowStatusAndPermissionType(
                                        config, field, workflowStatus, FieldStatusPermission.PermissionType.VIEWERS);
                        
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
            Set<Long> removedFieldIds
    ) {
        // TODO: Implementare rimozione ItemTypeSetRoles se necessario
    }















}

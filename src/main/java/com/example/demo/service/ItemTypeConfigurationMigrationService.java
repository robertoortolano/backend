package com.example.demo.service;

import com.example.demo.dto.ItemTypeConfigurationMigrationImpactDto;
import com.example.demo.dto.ItemTypeConfigurationMigrationRequest;
import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servizio per gestire la migrazione delle permission quando cambiano
 * FieldSet e/o Workflow in un ItemTypeConfiguration
 */
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
    private final FieldRepository fieldRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final TransitionRepository transitionRepository;
    private final FieldLookup fieldLookup;
    private final WorkflowLookup workflowLookup;
    private final FieldSetLookup fieldSetLookup;
    private final ItemTypeSetRepository itemTypeSetRepository;
    private final FieldConfigurationLookup fieldConfigurationLookup;
    
    // Servizi per PermissionAssignment (nuova struttura)
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;
    
    /**
     * Analizza l'impatto della migrazione quando cambiano FieldSet e/o Workflow
     */
    @Transactional(readOnly = true)
    public ItemTypeConfigurationMigrationImpactDto analyzeMigrationImpact(
            Tenant tenant,
            Long itemTypeConfigurationId,
            Long newFieldSetId,
            Long newWorkflowId
    ) {
        // Carica la configurazione esistente
        ItemTypeConfiguration oldConfig = itemTypeConfigurationRepository.findById(itemTypeConfigurationId)
                .orElseThrow(() -> new ApiException("ItemTypeConfiguration not found: " + itemTypeConfigurationId));
        
        if (!oldConfig.getTenant().getId().equals(tenant.getId())) {
            throw new ApiException("ItemTypeConfiguration does not belong to tenant");
        }
        
        // Carica le nuove entità
        FieldSet newFieldSet = null;
        Workflow newWorkflow = null;
        
        if (newFieldSetId != null) {
            newFieldSet = fieldSetLookup.getById(newFieldSetId, tenant);
        }
        
        if (newWorkflowId != null) {
            newWorkflow = workflowLookup.getByIdEntity(tenant, newWorkflowId);
        }
        
        FieldSet oldFieldSet = oldConfig.getFieldSet();
        Workflow oldWorkflow = oldConfig.getWorkflow();
        
        boolean fieldSetChanged = newFieldSet != null && !oldFieldSet.getId().equals(newFieldSet.getId());
        boolean workflowChanged = newWorkflow != null && !oldWorkflow.getId().equals(newWorkflow.getId());
        
        // Se nulla è cambiato, non serve migrazione
        if (!fieldSetChanged && !workflowChanged) {
            throw new ApiException("No changes detected. FieldSet and Workflow are the same.");
        }
        
        // Estrai informazioni su Field e WorkflowStatus/Transition
        ItemTypeConfigurationMigrationImpactDto.FieldSetInfo oldFieldSetInfo = extractFieldSetInfo(oldFieldSet);
        ItemTypeConfigurationMigrationImpactDto.FieldSetInfo newFieldSetInfo = newFieldSet != null 
                ? extractFieldSetInfo(newFieldSet) 
                : oldFieldSetInfo;
        
        ItemTypeConfigurationMigrationImpactDto.WorkflowInfo oldWorkflowInfo = extractWorkflowInfo(oldWorkflow);
        ItemTypeConfigurationMigrationImpactDto.WorkflowInfo newWorkflowInfo = newWorkflow != null 
                ? extractWorkflowInfo(newWorkflow) 
                : oldWorkflowInfo;
        
        // Analizza le permission
        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> fieldOwnerPermissions = 
                analyzeFieldOwnerPermissions(oldConfig, oldFieldSetInfo, newFieldSetInfo, fieldSetChanged);
        
        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> statusOwnerPermissions = 
                analyzeStatusOwnerPermissions(oldConfig, oldWorkflowInfo, newWorkflowInfo, workflowChanged);
        
        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> fieldStatusPermissions = 
                analyzeFieldStatusPermissions(oldConfig, oldFieldSetInfo, newFieldSetInfo, oldWorkflowInfo, newWorkflowInfo, fieldSetChanged, workflowChanged);
        
        List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> executorPermissions = 
                analyzeExecutorPermissions(oldConfig, oldWorkflowInfo, newWorkflowInfo, workflowChanged);
        
        // Calcola statistiche
        int totalPreservable = countPreservable(fieldOwnerPermissions, statusOwnerPermissions, fieldStatusPermissions, executorPermissions);
        int totalRemovable = countRemovable(fieldOwnerPermissions, statusOwnerPermissions, fieldStatusPermissions, executorPermissions);
        int totalNew = countNew(fieldOwnerPermissions, statusOwnerPermissions, fieldStatusPermissions, executorPermissions);
        int totalWithRoles = countWithRoles(fieldOwnerPermissions, statusOwnerPermissions, fieldStatusPermissions, executorPermissions);
        
        Long itemTypeSetId = getItemTypeSetIdForConfiguration(oldConfig);
        String itemTypeSetName = getItemTypeSetNameForConfiguration(oldConfig);
        
        return ItemTypeConfigurationMigrationImpactDto.builder()
                .itemTypeConfigurationId(oldConfig.getId())
                .itemTypeConfigurationName(oldConfig.getItemType().getName() + " Configuration")
                .itemTypeSetId(itemTypeSetId)
                .itemTypeSetName(itemTypeSetName)
                .itemTypeId(oldConfig.getItemType().getId())
                .itemTypeName(oldConfig.getItemType().getName())
                .oldFieldSet(oldFieldSetInfo)
                .newFieldSet(newFieldSetInfo)
                .fieldSetChanged(fieldSetChanged)
                .oldWorkflow(oldWorkflowInfo)
                .newWorkflow(newWorkflowInfo)
                .workflowChanged(workflowChanged)
                .fieldOwnerPermissions(fieldOwnerPermissions)
                .statusOwnerPermissions(statusOwnerPermissions)
                .fieldStatusPermissions(fieldStatusPermissions)
                .executorPermissions(executorPermissions)
                .totalPreservablePermissions(totalPreservable)
                .totalRemovablePermissions(totalRemovable)
                .totalNewPermissions(totalNew)
                .totalPermissionsWithRoles(totalWithRoles)
                .build();
    }
    
    /**
     * Applica la migrazione selettiva delle permission
     */
    public void applyMigration(
            Tenant tenant,
            Long itemTypeConfigurationId,
            ItemTypeConfigurationMigrationRequest request
    ) {
        if (!request.isValid()) {
            throw new ApiException("Invalid request: preserveAllPreservable and removeAll cannot both be true");
        }
        
        // Carica la configurazione esistente
        ItemTypeConfiguration config = itemTypeConfigurationRepository.findById(itemTypeConfigurationId)
                .orElseThrow(() -> new ApiException("ItemTypeConfiguration not found: " + itemTypeConfigurationId));
        
        if (!config.getTenant().getId().equals(tenant.getId())) {
            throw new ApiException("ItemTypeConfiguration does not belong to tenant");
        }
        
        // IMPORTANTE: Usa i nuovi FieldSetId e WorkflowId dalla request, non quelli attuali della config
        // Questo perché quando chiamiamo applyMigration, il database non è ancora stato aggiornato
        // con i nuovi valori che l'utente ha scelto nel form
        ItemTypeConfigurationMigrationImpactDto impact = analyzeMigrationImpact(
                tenant, itemTypeConfigurationId, 
                request.newFieldSetId(),
                request.newWorkflowId()
        );
        
        Set<Long> permissionsToPreserve;
        
        if (request.removeAll() != null && request.removeAll()) {
            // Rimuovi tutto
            permissionsToPreserve = new HashSet<>();
        } else if (request.preserveAllPreservable() != null && request.preserveAllPreservable()) {
            // Preserva tutto ciò che può essere preservato
            permissionsToPreserve = getAllPreservablePermissionIds(impact);
        } else {
            // IMPORTANTE: Se preservePermissionIds è un Set vuoto, significa che l'utente
            // ha deselezionato tutto intenzionalmente, quindi non preserviamo nulla.
            // Se è null, usiamo il default (getAllPreservablePermissionIds).
            if (request.preservePermissionIds() == null) {
                // Nessuna selezione specifica, usa il default
                permissionsToPreserve = getAllPreservablePermissionIds(impact);
            } else {
                // Usa la lista specifica fornita dall'utente (anche se vuota)
                permissionsToPreserve = request.preservePermissionIds();
            }
        }
        
        // Esegui la migrazione
        migrateFieldOwnerPermissions(config, impact, permissionsToPreserve);
        migrateStatusOwnerPermissions(config, impact, permissionsToPreserve);
        migrateFieldStatusPermissions(config, impact, permissionsToPreserve);
        migrateExecutorPermissions(config, impact, permissionsToPreserve);
        
        // IMPORTANTE: Aggiorna la configurazione con i nuovi FieldSet e Workflow PRIMA di creare le nuove permission
        // Questo perché createNewPermissions legge FieldSet e Workflow dalla configurazione
        if (request.newFieldSetId() != null) {
            FieldSet newFieldSet = fieldSetLookup.getById(request.newFieldSetId(), tenant);
            config.setFieldSet(newFieldSet);
        }
        if (request.newWorkflowId() != null) {
            Workflow newWorkflow = workflowLookup.getByIdEntity(tenant, request.newWorkflowId());
            config.setWorkflow(newWorkflow);
        }
        // Salva la configurazione aggiornata
        itemTypeConfigurationRepository.save(config);
        
        // Crea le nuove permission per entità nuove (non preservate)
        // Ora config.getFieldSet() e config.getWorkflow() puntano ai nuovi valori
        createNewPermissions(config, impact, permissionsToPreserve);
    }
    
    // ========== METODI PRIVATI DI ANALISI ==========
    
    private ItemTypeConfigurationMigrationImpactDto.FieldSetInfo extractFieldSetInfo(FieldSet fieldSet) {
        if (fieldSet == null) {
            return null;
        }
        
        Set<Long> fieldIds = fieldSet.getFieldSetEntries().stream()
                .map(entry -> entry.getFieldConfiguration().getField().getId())
                .collect(Collectors.toSet());
        
        List<ItemTypeConfigurationMigrationImpactDto.FieldInfo> fields = fieldIds.stream()
                .map(fieldId -> {
                    Field field = fieldLookup.getById(fieldId, fieldSet.getTenant());
                    return ItemTypeConfigurationMigrationImpactDto.FieldInfo.builder()
                            .fieldId(field.getId())
                            .fieldName(field.getName())
                            .build();
                })
                .collect(Collectors.toList());
        
        return ItemTypeConfigurationMigrationImpactDto.FieldSetInfo.builder()
                .fieldSetId(fieldSet.getId())
                .fieldSetName(fieldSet.getName())
                .fields(fields)
                .build();
    }
    
    private ItemTypeConfigurationMigrationImpactDto.WorkflowInfo extractWorkflowInfo(Workflow workflow) {
        if (workflow == null) {
            return null;
        }
        
        // Carica i WorkflowStatus con eager fetch dello Status per evitare lazy loading
        List<WorkflowStatus> statuses = workflowStatusRepository.findAllByWorkflowId(workflow.getId());
        // Assicuriamoci che lo Status sia caricato - potrebbe essere lazy
        List<ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo> workflowStatusInfos = statuses.stream()
                .map(ws -> {
                    // Force load dello Status se necessario
                    Status status = ws.getStatus();
                    if (status == null) {
                        log.warn("WorkflowStatus {} has null Status!", ws.getId());
                        return null;
                    }
                    // Accesso ai campi per forzare il caricamento
                    Long statusId = status.getId();
                    String statusName = status.getName();
                    
                    return ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo.builder()
                            .workflowStatusId(ws.getId())
                            .workflowStatusName(statusName)
                            .statusId(statusId)
                            .statusName(statusName)
                            .build();
                })
                .filter(java.util.Objects::nonNull) // Rimuovi eventuali null
                .collect(Collectors.toList());
        
        List<Transition> transitions = transitionRepository.findByWorkflowAndTenant(workflow, workflow.getTenant());
        List<ItemTypeConfigurationMigrationImpactDto.TransitionInfo> transitionInfos = transitions.stream()
                .map(t -> ItemTypeConfigurationMigrationImpactDto.TransitionInfo.builder()
                        .transitionId(t.getId())
                        .transitionName(t.getName())
                        .fromWorkflowStatusId(t.getFromStatus().getId())
                        .fromWorkflowStatusName(t.getFromStatus().getStatus().getName())
                        .toWorkflowStatusId(t.getToStatus().getId())
                        .toWorkflowStatusName(t.getToStatus().getStatus().getName())
                        .build())
                .collect(Collectors.toList());
        
        return ItemTypeConfigurationMigrationImpactDto.WorkflowInfo.builder()
                .workflowId(workflow.getId())
                .workflowName(workflow.getName())
                .workflowStatuses(workflowStatusInfos)
                .transitions(transitionInfos)
                .build();
    }
    
    private List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyzeFieldOwnerPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto.FieldSetInfo oldFieldSetInfo,
            ItemTypeConfigurationMigrationImpactDto.FieldSetInfo newFieldSetInfo,
            boolean fieldSetChanged
    ) {
        if (!fieldSetChanged) {
            return Collections.emptyList();
        }
        
        List<FieldOwnerPermission> existingPermissions = fieldOwnerPermissionRepository
                .findAllByItemTypeConfiguration(config);
        
        Set<Long> oldFieldIds = oldFieldSetInfo.getFields().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.FieldInfo::getFieldId)
                .collect(Collectors.toSet());
        
        Set<Long> newFieldIds = newFieldSetInfo.getFields().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.FieldInfo::getFieldId)
                .collect(Collectors.toSet());
        
        Map<Long, ItemTypeConfigurationMigrationImpactDto.FieldInfo> newFieldsMap = newFieldSetInfo.getFields().stream()
                .collect(Collectors.toMap(
                        ItemTypeConfigurationMigrationImpactDto.FieldInfo::getFieldId,
                        java.util.function.Function.identity()
                ));
        
        Long itemTypeSetId = getItemTypeSetIdForConfiguration(config);
        Tenant tenant = config.getTenant();
        FieldSet oldFieldSet = config.getFieldSet();
        
        return existingPermissions.stream()
                .map(perm -> {
                    Long fieldId = perm.getField().getId();
                    boolean canPreserve = newFieldIds.contains(fieldId);
                    ItemTypeConfigurationMigrationImpactDto.FieldInfo matchingField = canPreserve 
                            ? newFieldsMap.get(fieldId) 
                            : null;
                    
                    // Recupera ruoli da PermissionAssignment invece di getAssignedRoles()
                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "FieldOwnerPermission", perm.getId(), tenant);
                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());
                    
                    boolean hasRoles = !assignedRoles.isEmpty();
                    
                    // Trova grant globali e di progetto
                    // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                    Long grantId = null;
                    String grantName = null;
                    List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrantsList = new ArrayList<>();
                    
                    // IMPORTANTE: Cerca le grant anche se non ci sono ruoli assegnati
                    // perché le grant possono esistere indipendentemente dai ruoli
                    if (itemTypeSetId != null) {
                        // Trova la FieldConfiguration nel vecchio FieldSet per questo Field
                        FieldConfiguration fieldConfig = oldFieldSet.getFieldSetEntries().stream()
                                .map(FieldSetEntry::getFieldConfiguration)
                                .filter(fc -> fc.getField().getId().equals(fieldId))
                                .findFirst()
                                .orElse(null);
                        
                        if (fieldConfig != null) {
                            // RIMOSSO: ItemTypeSetRole eliminata - recupera grant da PermissionAssignment
                            // Riutilizza assignmentOpt già definito sopra per i ruoli
                            if (assignmentOpt.isPresent()) {
                                PermissionAssignment assignment = assignmentOpt.get();
                                // Recupera grant globale
                                if (assignment.getGrant() != null) {
                                    Grant grant = assignment.getGrant();
                                    grantId = grant.getId();
                                    grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
                                }
                            }
                            
                            // Recupera grant di progetto da ProjectPermissionAssignmentService
                            ItemTypeSet itemTypeSet = itemTypeSetRepository.findById(itemTypeSetId)
                                    .orElse(null);
                            if (itemTypeSet != null) {
                                // Se è un ItemTypeSet di progetto, controlla solo quel progetto
                                if (itemTypeSet.getProject() != null) {
                                    Optional<PermissionAssignment> projectAssignmentOpt = 
                                            projectPermissionAssignmentService.getProjectAssignment(
                                                    "FieldStatusPermission", perm.getId(), 
                                                    itemTypeSet.getProject().getId(), tenant);
                                    if (projectAssignmentOpt.isPresent() && 
                                            projectAssignmentOpt.get().getGrant() != null) {
                                        projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                                .projectId(itemTypeSet.getProject().getId())
                                                .projectName(itemTypeSet.getProject().getName())
                                                .build());
                                    }
                                } else {
                                    // Se è un ItemTypeSet globale, controlla tutti i progetti associati
                                    for (Project project : itemTypeSet.getProjectsAssociation()) {
                                        Optional<PermissionAssignment> projectAssignmentOpt = 
                                                projectPermissionAssignmentService.getProjectAssignment(
                                                        "FieldOwnerPermission", perm.getId(), 
                                                        project.getId(), tenant);
                                        if (projectAssignmentOpt.isPresent() && 
                                                projectAssignmentOpt.get().getGrant() != null) {
                                            projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                                    .projectId(project.getId())
                                                    .projectName(project.getName())
                                                    .build());
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // hasAssignments è true se ci sono ruoli O grant
                    boolean hasAssignments = hasRoles || grantId != null || !projectGrantsList.isEmpty();
                    boolean defaultPreserve = canPreserve && hasAssignments;
                    
                    return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                            .permissionId(perm.getId())
                            .permissionType("FIELD_OWNERS")
                            .entityId(fieldId)
                            .entityName(perm.getField().getName())
                            .matchingEntityId(canPreserve && matchingField != null ? matchingField.getFieldId() : null)
                            .matchingEntityName(canPreserve && matchingField != null ? matchingField.getFieldName() : null)
                            .assignedRoles(assignedRoles)
                            .hasAssignments(hasAssignments)
                            .canBePreserved(canPreserve)
                            .defaultPreserve(defaultPreserve)
                            .suggestedAction(canPreserve ? "PRESERVE" : "REMOVE")
                            .itemTypeSetId(itemTypeSetId)
                            .itemTypeSetName(getItemTypeSetNameForConfiguration(config))
                            .projectId(config.getProject() != null ? config.getProject().getId() : null)
                            .projectName(config.getProject() != null ? config.getProject().getName() : null)
                            // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                            .grantId(grantId)
                            .grantName(grantName)
                            .projectGrants(projectGrantsList)
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    private List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyzeStatusOwnerPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto.WorkflowInfo oldWorkflowInfo,
            ItemTypeConfigurationMigrationImpactDto.WorkflowInfo newWorkflowInfo,
            boolean workflowChanged
    ) {
        if (!workflowChanged) {
            return Collections.emptyList();
        }
        
        List<StatusOwnerPermission> existingPermissions = statusOwnerPermissionRepository
                .findAllByItemTypeConfiguration(config);
        
        // IMPORTANTE: Matching per Status.id (non WorkflowStatus.id) perché lo stesso Status
        // può esistere in workflow diversi con WorkflowStatus diversi
        Set<Long> oldStatusIds = oldWorkflowInfo.getWorkflowStatuses().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo::getStatusId)
                .collect(Collectors.toSet());
        
        Set<Long> newStatusIds = newWorkflowInfo.getWorkflowStatuses().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo::getStatusId)
                .collect(Collectors.toSet());
        
        // Map per Status.id -> WorkflowStatusInfo nel nuovo workflow
        Map<Long, ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo> newStatusesMap = newWorkflowInfo.getWorkflowStatuses().stream()
                .collect(Collectors.toMap(
                        ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo::getStatusId,
                        java.util.function.Function.identity(),
                        (existing, replacement) -> existing // In caso di duplicati, mantieni il primo
                ));
        
        Long itemTypeSetId = getItemTypeSetIdForConfiguration(config);
        Tenant tenant = config.getTenant();
        
        return existingPermissions.stream()
                .map(perm -> {
                    // Usa Status.id (non WorkflowStatus.id) per il matching
                    // IMPORTANTE: perm.getWorkflowStatus().getStatus() deve essere già caricato dal JOIN FETCH
                    Status status = perm.getWorkflowStatus().getStatus();
                    Long statusId = status.getId();
                    String statusName = status.getName();
                    WorkflowStatus workflowStatus = perm.getWorkflowStatus();
                    
                    boolean canPreserve = newStatusIds.contains(statusId);
                    ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo matchingStatus = canPreserve 
                            ? newStatusesMap.get(statusId) 
                            : null;
                    
                    // Recupera ruoli da PermissionAssignment invece di getAssignedRoles()
                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "StatusOwnerPermission", perm.getId(), tenant);
                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());
                    
                    boolean hasRoles = !assignedRoles.isEmpty();
                    
                    // Trova grant globali e di progetto
                    // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                    Long grantId = null;
                    String grantName = null;
                    List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrantsList = new ArrayList<>();
                    
                    // IMPORTANTE: Cerca le grant anche se non ci sono ruoli assegnati
                    // perché le grant possono esistere indipendentemente dai ruoli
                    if (itemTypeSetId != null && workflowStatus != null) {
                        // RIMOSSO: ItemTypeSetRole eliminata - recupera grant da PermissionAssignment
                        // Riutilizza assignmentOpt già definito sopra per i ruoli
                        if (assignmentOpt.isPresent()) {
                            PermissionAssignment assignment = assignmentOpt.get();
                            // Recupera grant globale
                            if (assignment.getGrant() != null) {
                                Grant grant = assignment.getGrant();
                                grantId = grant.getId();
                                grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
                            }
                        }
                        
                        // Recupera grant di progetto da ProjectPermissionAssignmentService
                        ItemTypeSet itemTypeSet = itemTypeSetRepository.findById(itemTypeSetId)
                                .orElse(null);
                        if (itemTypeSet != null) {
                            // Se è un ItemTypeSet di progetto, controlla solo quel progetto
                            if (itemTypeSet.getProject() != null) {
                                Optional<PermissionAssignment> projectAssignmentOpt = 
                                        projectPermissionAssignmentService.getProjectAssignment(
                                                "StatusOwnerPermission", perm.getId(), 
                                                itemTypeSet.getProject().getId(), tenant);
                                if (projectAssignmentOpt.isPresent() && 
                                        projectAssignmentOpt.get().getGrant() != null) {
                                    projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                            .projectId(itemTypeSet.getProject().getId())
                                            .projectName(itemTypeSet.getProject().getName())
                                            .build());
                                }
                            } else {
                                // Se è un ItemTypeSet globale, controlla tutti i progetti associati
                                for (Project project : itemTypeSet.getProjectsAssociation()) {
                                    Optional<PermissionAssignment> projectAssignmentOpt = 
                                            projectPermissionAssignmentService.getProjectAssignment(
                                                    "StatusOwnerPermission", perm.getId(), 
                                                    project.getId(), tenant);
                                    if (projectAssignmentOpt.isPresent() && 
                                            projectAssignmentOpt.get().getGrant() != null) {
                                        projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                                .projectId(project.getId())
                                                .projectName(project.getName())
                                                .build());
                                    }
                                }
                            }
                        }
                    }
                    
                    // hasAssignments è true se ci sono ruoli O grant
                    boolean hasAssignments = hasRoles || grantId != null || !projectGrantsList.isEmpty();
                    boolean defaultPreserve = canPreserve && hasAssignments;
                    
                    return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                            .permissionId(perm.getId())
                            .permissionType("STATUS_OWNERS")
                            .entityId(statusId) // Status.id (non WorkflowStatus.id)
                            .entityName(statusName)
                            .matchingEntityId(canPreserve && matchingStatus != null ? matchingStatus.getWorkflowStatusId() : null)
                            .matchingEntityName(canPreserve && matchingStatus != null ? matchingStatus.getWorkflowStatusName() : null)
                            .assignedRoles(assignedRoles)
                            .hasAssignments(hasAssignments)
                            .canBePreserved(canPreserve)
                            .defaultPreserve(defaultPreserve)
                            .suggestedAction(canPreserve ? "PRESERVE" : "REMOVE")
                            .itemTypeSetId(itemTypeSetId)
                            .itemTypeSetName(getItemTypeSetNameForConfiguration(config))
                            .projectId(config.getProject() != null ? config.getProject().getId() : null)
                            .projectName(config.getProject() != null ? config.getProject().getName() : null)
                            // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                            .grantId(grantId)
                            .grantName(grantName)
                            .projectGrants(projectGrantsList)
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    private List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyzeFieldStatusPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto.FieldSetInfo oldFieldSetInfo,
            ItemTypeConfigurationMigrationImpactDto.FieldSetInfo newFieldSetInfo,
            ItemTypeConfigurationMigrationImpactDto.WorkflowInfo oldWorkflowInfo,
            ItemTypeConfigurationMigrationImpactDto.WorkflowInfo newWorkflowInfo,
            boolean fieldSetChanged,
            boolean workflowChanged
    ) {
        if (!fieldSetChanged && !workflowChanged) {
            return Collections.emptyList();
        }
        
        List<FieldStatusPermission> existingPermissions = fieldStatusPermissionRepository
                .findAllByItemTypeConfiguration(config);
        
        Set<Long> oldFieldIds = oldFieldSetInfo.getFields().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.FieldInfo::getFieldId)
                .collect(Collectors.toSet());
        
        Set<Long> newFieldIds = newFieldSetInfo.getFields().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.FieldInfo::getFieldId)
                .collect(Collectors.toSet());
        
        Set<Long> oldStatusIds = oldWorkflowInfo.getWorkflowStatuses().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo::getWorkflowStatusId)
                .collect(Collectors.toSet());
        
        // IMPORTANTE: Per FieldStatusPermission, usiamo Status.id (non WorkflowStatus.id) per il matching
        // perché lo stesso Status può esistere in workflow diversi con WorkflowStatus diversi
        Set<Long> newStatusIds = newWorkflowInfo.getWorkflowStatuses().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo::getStatusId)
                .collect(Collectors.toSet());
        
        Map<Long, ItemTypeConfigurationMigrationImpactDto.FieldInfo> newFieldsMap = newFieldSetInfo.getFields().stream()
                .collect(Collectors.toMap(
                        ItemTypeConfigurationMigrationImpactDto.FieldInfo::getFieldId,
                        java.util.function.Function.identity()
                ));
        
        // Map per Status.id -> WorkflowStatusInfo nel nuovo workflow
        Map<Long, ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo> newStatusesMap = newWorkflowInfo.getWorkflowStatuses().stream()
                .collect(Collectors.toMap(
                        ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo::getStatusId,
                        java.util.function.Function.identity(),
                        (existing, replacement) -> existing // In caso di duplicati, mantieni il primo
                ));
        
        Long itemTypeSetId = getItemTypeSetIdForConfiguration(config);
        Tenant tenant = config.getTenant();
        FieldSet oldFieldSet = config.getFieldSet();
        
        return existingPermissions.stream()
                .map(perm -> {
                    Long fieldId = perm.getField().getId();
                    // Usa Status.id (non WorkflowStatus.id) per il matching
                    Long statusId = perm.getWorkflowStatus().getStatus().getId();
                    
                    // Può essere preservata solo se ENTRAMBI Field e Status esistono nel nuovo stato
                    boolean fieldExists = newFieldIds.contains(fieldId);
                    boolean statusExists = newStatusIds.contains(statusId);
                    boolean canPreserve = fieldExists && statusExists;
                    
                    ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo matchingStatus = statusExists 
                            ? newStatusesMap.get(statusId) 
                            : null;
                    
                    // Recupera ruoli da PermissionAssignment invece di getAssignedRoles()
                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "FieldStatusPermission", perm.getId(), tenant);
                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());
                    
                    boolean hasRoles = !assignedRoles.isEmpty();
                    
                    // Trova grant globali e di progetto
                    // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                    Long grantId = null;
                    String grantName = null;
                    List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrantsList = new ArrayList<>();
                    
                    // IMPORTANTE: Cerca le grant anche se non ci sono ruoli assegnati
                    // perché le grant possono esistere indipendentemente dai ruoli
                    if (itemTypeSetId != null) {
                        // Trova la FieldConfiguration nel vecchio FieldSet per questo Field
                        FieldConfiguration fieldConfig = oldFieldSet.getFieldSetEntries().stream()
                                .map(FieldSetEntry::getFieldConfiguration)
                                .filter(fc -> fc.getField().getId().equals(fieldId))
                                .findFirst()
                                .orElse(null);
                        
                        if (fieldConfig != null) {
                            // RIMOSSO: ItemTypeSetRole eliminata - recupera grant da PermissionAssignment
                            // Riutilizza assignmentOpt già definito sopra per i ruoli
                            if (assignmentOpt.isPresent()) {
                                PermissionAssignment assignment = assignmentOpt.get();
                                // Recupera grant globale
                                if (assignment.getGrant() != null) {
                                    Grant grant = assignment.getGrant();
                                    grantId = grant.getId();
                                    grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
                                }
                            }
                            
                            // Recupera grant di progetto da ProjectPermissionAssignmentService
                            ItemTypeSet itemTypeSet = itemTypeSetRepository.findById(itemTypeSetId)
                                    .orElse(null);
                            if (itemTypeSet != null) {
                                // Se è un ItemTypeSet di progetto, controlla solo quel progetto
                                if (itemTypeSet.getProject() != null) {
                                    Optional<PermissionAssignment> projectAssignmentOpt = 
                                            projectPermissionAssignmentService.getProjectAssignment(
                                                    "FieldStatusPermission", perm.getId(), 
                                                    itemTypeSet.getProject().getId(), tenant);
                                    if (projectAssignmentOpt.isPresent() && 
                                            projectAssignmentOpt.get().getGrant() != null) {
                                        projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                                .projectId(itemTypeSet.getProject().getId())
                                                .projectName(itemTypeSet.getProject().getName())
                                                .build());
                                    }
                                } else {
                                    // Se è un ItemTypeSet globale, controlla tutti i progetti associati
                                    for (Project project : itemTypeSet.getProjectsAssociation()) {
                                        Optional<PermissionAssignment> projectAssignmentOpt = 
                                                projectPermissionAssignmentService.getProjectAssignment(
                                                        "FieldOwnerPermission", perm.getId(), 
                                                        project.getId(), tenant);
                                        if (projectAssignmentOpt.isPresent() && 
                                                projectAssignmentOpt.get().getGrant() != null) {
                                            projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                                    .projectId(project.getId())
                                                    .projectName(project.getName())
                                                    .build());
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // hasAssignments è true se ci sono ruoli O grant
                    boolean hasAssignments = hasRoles || grantId != null || !projectGrantsList.isEmpty();
                    boolean defaultPreserve = canPreserve && hasAssignments;
                    
                    String suggestedAction = canPreserve ? "PRESERVE" : "REMOVE";
                    
                    return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                            .permissionId(perm.getId())
                            .permissionType(perm.getPermissionType().toString()) // "EDITORS" o "VIEWERS"
                            .entityId(null) // Non applicabile per FieldStatus
                            .entityName(null)
                            .fieldId(fieldId)
                            .fieldName(perm.getField().getName())
                            .workflowStatusId(statusId)
                            .workflowStatusName(perm.getWorkflowStatus().getStatus().getName())
                            .matchingEntityId(null) // Non usato per FieldStatus
                            .matchingEntityName(null)
                            .assignedRoles(assignedRoles)
                            .hasAssignments(hasAssignments)
                            .canBePreserved(canPreserve)
                            .defaultPreserve(defaultPreserve)
                            .suggestedAction(suggestedAction)
                            .itemTypeSetId(itemTypeSetId)
                            .itemTypeSetName(getItemTypeSetNameForConfiguration(config))
                            .projectId(config.getProject() != null ? config.getProject().getId() : null)
                            .projectName(config.getProject() != null ? config.getProject().getName() : null)
                            // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                            .grantId(grantId)
                            .grantName(grantName)
                            .projectGrants(projectGrantsList)
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    private List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyzeExecutorPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto.WorkflowInfo oldWorkflowInfo,
            ItemTypeConfigurationMigrationImpactDto.WorkflowInfo newWorkflowInfo,
            boolean workflowChanged
    ) {
        if (!workflowChanged) {
            return Collections.emptyList();
        }
        
        List<ExecutorPermission> existingPermissions = executorPermissionRepository
                .findAllByItemTypeConfiguration(config);
        
        Set<Long> oldTransitionIds = oldWorkflowInfo.getTransitions().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.TransitionInfo::getTransitionId)
                .collect(Collectors.toSet());
        
        Set<Long> newTransitionIds = newWorkflowInfo.getTransitions().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.TransitionInfo::getTransitionId)
                .collect(Collectors.toSet());
        
        Map<Long, ItemTypeConfigurationMigrationImpactDto.TransitionInfo> newTransitionsMap = newWorkflowInfo.getTransitions().stream()
                .collect(Collectors.toMap(
                        ItemTypeConfigurationMigrationImpactDto.TransitionInfo::getTransitionId,
                        java.util.function.Function.identity()
                ));
        
        Long itemTypeSetId = getItemTypeSetIdForConfiguration(config);
        Tenant tenant = config.getTenant();
        
        return existingPermissions.stream()
                .map(perm -> {
                    Transition transition = perm.getTransition();
                    Long transitionId = transition.getId();
                    // Matching conservativo: solo se stesso ID (stessa Transition)
                    // Le Transition sono specifiche del workflow, quindi il matching per ID è corretto
                    boolean canPreserve = newTransitionIds.contains(transitionId);
                    ItemTypeConfigurationMigrationImpactDto.TransitionInfo matchingTransition = canPreserve 
                            ? newTransitionsMap.get(transitionId) 
                            : null;
                    
                    // Recupera ruoli da PermissionAssignment invece di getAssignedRoles()
                    Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                            "ExecutorPermission", perm.getId(), tenant);
                    List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList()))
                            .orElse(new ArrayList<>());
                    
                    boolean hasRoles = !assignedRoles.isEmpty();
                    
                    // Trova grant globali e di progetto
                    // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                    Long grantId = null;
                    String grantName = null;
                    List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrantsList = new ArrayList<>();
                    
                    // IMPORTANTE: Cerca le grant anche se non ci sono ruoli assegnati
                    // perché le grant possono esistere indipendentemente dai ruoli
                    if (itemTypeSetId != null) {
                        // RIMOSSO: ItemTypeSetRole eliminata - recupera grant da PermissionAssignment
                        // Riutilizza assignmentOpt già definito sopra per i ruoli
                        if (assignmentOpt.isPresent()) {
                            PermissionAssignment assignment = assignmentOpt.get();
                            // Recupera grant globale
                            if (assignment.getGrant() != null) {
                                Grant grant = assignment.getGrant();
                                grantId = grant.getId();
                                grantName = grant.getRole() != null ? grant.getRole().getName() : "Grant globale";
                            }
                        }
                        
                        // Recupera grant di progetto da ProjectPermissionAssignmentService
                        ItemTypeSet itemTypeSet = itemTypeSetRepository.findById(itemTypeSetId)
                                .orElse(null);
                        if (itemTypeSet != null) {
                            // Se è un ItemTypeSet di progetto, controlla solo quel progetto
                            if (itemTypeSet.getProject() != null) {
                                Optional<PermissionAssignment> projectAssignmentOpt = 
                                        projectPermissionAssignmentService.getProjectAssignment(
                                                "ExecutorPermission", perm.getId(), 
                                                itemTypeSet.getProject().getId(), tenant);
                                if (projectAssignmentOpt.isPresent() && 
                                        projectAssignmentOpt.get().getGrant() != null) {
                                    projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                            .projectId(itemTypeSet.getProject().getId())
                                            .projectName(itemTypeSet.getProject().getName())
                                            .build());
                                }
                            } else {
                                // Se è un ItemTypeSet globale, controlla tutti i progetti associati
                                for (Project project : itemTypeSet.getProjectsAssociation()) {
                                    Optional<PermissionAssignment> projectAssignmentOpt = 
                                            projectPermissionAssignmentService.getProjectAssignment(
                                                    "ExecutorPermission", perm.getId(), 
                                                    project.getId(), tenant);
                                    if (projectAssignmentOpt.isPresent() && 
                                            projectAssignmentOpt.get().getGrant() != null) {
                                        projectGrantsList.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                                .projectId(project.getId())
                                                .projectName(project.getName())
                                                .build());
                                    }
                                }
                            }
                        }
                    }
                    
                    // hasAssignments è true se ci sono ruoli O grant
                    boolean hasAssignments = hasRoles || grantId != null || !projectGrantsList.isEmpty();
                    boolean defaultPreserve = canPreserve && hasAssignments;
                    
                    // Estrai informazioni sulla Transition
                    String transitionName = transition.getName() != null && !transition.getName().isEmpty() 
                            ? transition.getName() 
                            : null;
                    String fromStatusName = transition.getFromStatus() != null && transition.getFromStatus().getStatus() != null
                            ? transition.getFromStatus().getStatus().getName()
                            : null;
                    String toStatusName = transition.getToStatus() != null && transition.getToStatus().getStatus() != null
                            ? transition.getToStatus().getStatus().getName()
                            : null;
                    
                    return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                            .permissionId(perm.getId())
                            .permissionType("EXECUTORS")
                            .entityId(transitionId)
                            .entityName(transitionName != null ? transitionName : (fromStatusName + " -> " + toStatusName))
                            .matchingEntityId(canPreserve && matchingTransition != null ? matchingTransition.getTransitionId() : null)
                            .matchingEntityName(canPreserve && matchingTransition != null ? matchingTransition.getTransitionName() : null)
                            .assignedRoles(assignedRoles)
                            .hasAssignments(hasAssignments)
                            .canBePreserved(canPreserve)
                            .defaultPreserve(defaultPreserve)
                            .suggestedAction(canPreserve ? "PRESERVE" : "REMOVE")
                            .itemTypeSetId(itemTypeSetId)
                            .itemTypeSetName(getItemTypeSetNameForConfiguration(config))
                            .projectId(config.getProject() != null ? config.getProject().getId() : null)
                            .projectName(config.getProject() != null ? config.getProject().getName() : null)
                            // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata
                            .grantId(grantId)
                            .grantName(grantName)
                            .projectGrants(projectGrantsList)
                            .fromStatusName(fromStatusName)
                            .toStatusName(toStatusName)
                            .transitionName(transitionName)
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    // ========== METODI PRIVATI DI MIGRAZIONE ==========
    
    private void migrateFieldOwnerPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve
    ) {
        for (ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact permImpact : impact.getFieldOwnerPermissions()) {
            if (permissionsToPreserve.contains(permImpact.getPermissionId())) {
                // Preserva: la permission rimane, ma potrebbe dover essere associata a una nuova configurazione
                // In realtà, se il Field è lo stesso, la permission può rimanere invariata
                // Ma se stiamo cambiando ItemTypeConfiguration, dobbiamo gestire diversamente
                // Per ora, manteniamo la permission così com'è
            } else {
                // Rimuovi la permission
                fieldOwnerPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        }
    }
    
    private void migrateStatusOwnerPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve
    ) {
        for (ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact permImpact : impact.getStatusOwnerPermissions()) {
            if (permissionsToPreserve.contains(permImpact.getPermissionId())) {
            } else {
                statusOwnerPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        }
    }
    
    private void migrateFieldStatusPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve
    ) {
        for (ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact permImpact : impact.getFieldStatusPermissions()) {
            if (permissionsToPreserve.contains(permImpact.getPermissionId())) {
            } else {
                fieldStatusPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        }
    }
    
    private void migrateExecutorPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> permissionsToPreserve
    ) {
        for (ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact permImpact : impact.getExecutorPermissions()) {
            if (permissionsToPreserve.contains(permImpact.getPermissionId())) {
            } else {
                executorPermissionRepository.deleteById(permImpact.getPermissionId());
            }
        }
    }
    
    private void createNewPermissions(
            ItemTypeConfiguration config,
            ItemTypeConfigurationMigrationImpactDto impact,
            Set<Long> preservedPermissionIds
    ) {
        // Crea nuove permission per entità nuove (non presenti nelle vecchie permission preservate)
        // Questa logica verrà implementata dopo, per ora usiamo il servizio esistente
        // che crea tutte le permission necessarie
        itemTypePermissionService.createPermissionsForItemTypeConfiguration(config);
    }
    
    // ========== METODI DI UTILITÀ ==========
    
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
        
        return preservable;
    }
    
    @SafeVarargs
    private final int countPreservable(
            List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact>... permissionLists
    ) {
        return Arrays.stream(permissionLists)
                .flatMap(List::stream)
                .mapToInt(p -> p.isCanBePreserved() ? 1 : 0)
                .sum();
    }
    
    @SafeVarargs
    private final int countRemovable(
            List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact>... permissionLists
    ) {
        return Arrays.stream(permissionLists)
                .flatMap(List::stream)
                .mapToInt(p -> !p.isCanBePreserved() ? 1 : 0)
                .sum();
    }
    
    @SafeVarargs
    private final int countNew(
            List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact>... permissionLists
    ) {
        // Le permission "nuove" sono quelle che devono essere create
        // Vengono identificate durante la creazione delle nuove permission
        return 0; // TODO: Calcolare basandosi su entità nuove nel nuovo stato
    }
    
    @SafeVarargs
    private final int countWithRoles(
            List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact>... permissionLists
    ) {
        return Arrays.stream(permissionLists)
                .flatMap(List::stream)
                .mapToInt(p -> p.isHasAssignments() ? 1 : 0)
                .sum();
    }
    
    // Injection del servizio esistente
    private final ItemTypePermissionService itemTypePermissionService;
    
    // ========== METODI DI UTILITÀ AGGIUNTIVI ==========
    
    /**
     * Trova l'ItemTypeSet che contiene questa ItemTypeConfiguration, filtrato per Tenant (sicurezza)
     */
    private Long getItemTypeSetIdForConfiguration(ItemTypeConfiguration config) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByItemTypeConfigurations_IdAndTenant(config.getId(), config.getTenant())
                .stream()
                .findFirst()
                .orElse(null);
        return itemTypeSet != null ? itemTypeSet.getId() : null;
    }
    
    private String getItemTypeSetNameForConfiguration(ItemTypeConfiguration config) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByItemTypeConfigurations_IdAndTenant(config.getId(), config.getTenant())
                .stream()
                .findFirst()
                .orElse(null);
        return itemTypeSet != null ? itemTypeSet.getName() : null;
    }
}


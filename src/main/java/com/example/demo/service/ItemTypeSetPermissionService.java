package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.enums.ItemTypeSetRoleType;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ItemTypeSetPermissionService {
    
    private final ItemTypeSetRepository itemTypeSetRepository;
    private final WorkerPermissionRepository workerPermissionRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final CreatorPermissionRepository creatorPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final ItemTypePermissionService itemTypePermissionService;
    private final RoleRepository roleRepository;
    private final EntityManager entityManager;
    private final ItemTypeSetRoleRepository itemTypeSetRoleRepository;
    private final ProjectItemTypeSetRoleGrantRepository projectItemTypeSetRoleGrantRepository;
    
    /**
     * Crea automaticamente tutte le permissions per un ItemTypeSet
     */
    public void createPermissionsForItemTypeSet(Long itemTypeSetId, Tenant tenant) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findById(itemTypeSetId)
                .orElseThrow(() -> new ApiException("ItemTypeSet not found"));
        
        // Crea le permissions per ogni ItemTypeConfiguration
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            itemTypePermissionService.createPermissionsForItemTypeConfiguration(config);
        }
    }
    
    /**
     * Ottiene tutte le permissions per un ItemTypeSet, raggruppate per tipo.
     * Se projectId è specificato, include anche le grant di progetto.
     */
    public Map<String, List<Map<String, Object>>> getPermissionsByItemTypeSet(Long itemTypeSetId, Tenant tenant, Long projectId) {
        try {
            ItemTypeSet itemTypeSet = itemTypeSetRepository.findByIdWithAllRelations(itemTypeSetId, tenant)
                    .orElseThrow(() -> new ApiException("ItemTypeSet not found"));
            
            Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        
        // Worker permissions
        List<Map<String, Object>> workers = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<WorkerPermission> permissions = workerPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (WorkerPermission perm : permissions) {
                Map<String, Object> worker = new HashMap<>();
                worker.put("id", perm.getId());
                worker.put("name", "Workers");
                Map<String, Object> itemTypeMap = new HashMap<>();
                itemTypeMap.put("id", config.getItemType().getId());
                itemTypeMap.put("name", config.getItemType().getName());
                worker.put("itemType", itemTypeMap);
                worker.put("assignedRolesCount", perm.getAssignedRoles().size());
                
                // Trova o crea l'ItemTypeSetRole corrispondente
                Optional<ItemTypeSetRole> itemTypeSetRoleOpt = itemTypeSetRoleRepository
                    .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                        itemTypeSet.getId(), "ItemType", config.getItemType().getId(), 
                        ItemTypeSetRoleType.WORKERS, tenant.getId());
                
                ItemTypeSetRole itemTypeSetRole;
                if (itemTypeSetRoleOpt.isPresent()) {
                    itemTypeSetRole = itemTypeSetRoleOpt.get();
                } else {
                    // Crea l'ItemTypeSetRole se non esiste
                    itemTypeSetRole = ItemTypeSetRole.builder()
                        .roleType(ItemTypeSetRoleType.WORKERS)
                        .name("Worker for " + config.getItemType().getName())
                        .description("Worker role for ItemType: " + config.getItemType().getName())
                        .itemTypeSet(itemTypeSet)
                        .relatedEntityType("ItemType")
                        .relatedEntityId(config.getItemType().getId())
                        .tenant(tenant)
                        .build();
                    itemTypeSetRole = itemTypeSetRoleRepository.save(itemTypeSetRole);
                }
                
                worker.put("itemTypeSetRoleId", itemTypeSetRole.getId());
                // Aggiungi informazioni su Grant e RoleTemplate se presenti
                if (itemTypeSetRole.getGrant() != null) {
                    worker.put("grantId", itemTypeSetRole.getGrant().getId());
                    worker.put("grantName", "Grant diretto");
                }
                if (itemTypeSetRole.getRoleTemplate() != null) {
                    worker.put("roleTemplateId", itemTypeSetRole.getRoleTemplate().getId());
                    worker.put("roleTemplateName", itemTypeSetRole.getRoleTemplate().getName());
                }
                worker.put("assignmentType", itemTypeSetRole.getAssignmentType());
                
                // Aggiungi informazioni su Grant di progetto se disponibile
                addProjectGrantInfo(worker, itemTypeSetRole.getId(), projectId, tenant.getId());
                
                // Calcola se ci sono effettivamente assegnazioni (ruoli custom o grant)
                boolean hasAssignments = !perm.getAssignedRoles().isEmpty();
                if (itemTypeSetRole.getGrant() != null) {
                    hasAssignments = true;
                }
                worker.put("hasAssignments", hasAssignments);
                
                // Aggiungi i dati delle assegnazioni esistenti
                List<Map<String, Object>> assignedRoles = new ArrayList<>();
                for (Role role : perm.getAssignedRoles()) {
                    Map<String, Object> roleMap = new HashMap<>();
                    roleMap.put("id", role.getId());
                    roleMap.put("name", role.getName());
                    roleMap.put("description", role.getDescription());
                    assignedRoles.add(roleMap);
                }
                worker.put("assignedRoles", assignedRoles);
                
                workers.add(worker);
            }
        }
        result.put("WORKERS", workers);
        
        // StatusOwner permissions
        List<Map<String, Object>> statusOwners = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<StatusOwnerPermission> permissions = statusOwnerPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (StatusOwnerPermission perm : permissions) {
                Map<String, Object> statusOwner = new HashMap<>();
                statusOwner.put("id", perm.getId());
                statusOwner.put("name", "Status Owners");
                Map<String, Object> workflowStatusMap = new HashMap<>();
                workflowStatusMap.put("id", perm.getWorkflowStatus().getId());
                workflowStatusMap.put("name", perm.getWorkflowStatus().getStatus().getName());
                statusOwner.put("workflowStatus", workflowStatusMap);
                
                Map<String, Object> workflowMap = new HashMap<>();
                workflowMap.put("id", config.getWorkflow().getId());
                workflowMap.put("name", config.getWorkflow().getName());
                statusOwner.put("workflow", workflowMap);
                
                Map<String, Object> itemTypeMap2 = new HashMap<>();
                itemTypeMap2.put("id", config.getItemType().getId());
                itemTypeMap2.put("name", config.getItemType().getName());
                statusOwner.put("itemType", itemTypeMap2);
                statusOwner.put("assignedRolesCount", perm.getAssignedRoles().size());
                
                // Trova o crea l'ItemTypeSetRole corrispondente per STATUS_OWNERS
                if (perm.getWorkflowStatus() != null) {
                    Optional<ItemTypeSetRole> itemTypeSetRoleOpt = itemTypeSetRoleRepository
                        .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                            itemTypeSet.getId(), "WorkflowStatus", perm.getWorkflowStatus().getId(), 
                            ItemTypeSetRoleType.STATUS_OWNERS, tenant.getId());
                    
                    ItemTypeSetRole itemTypeSetRole;
                    if (itemTypeSetRoleOpt.isPresent()) {
                        itemTypeSetRole = itemTypeSetRoleOpt.get();
                    } else {
                        // Crea l'ItemTypeSetRole se non esiste
                        itemTypeSetRole = ItemTypeSetRole.builder()
                            .roleType(ItemTypeSetRoleType.STATUS_OWNERS)
                            .name("Status Owner for " + perm.getWorkflowStatus().getStatus().getName() + " in " + config.getWorkflow().getName())
                            .description("Status Owner role for WorkflowStatus: " + perm.getWorkflowStatus().getStatus().getName())
                            .itemTypeSet(itemTypeSet)
                            .relatedEntityType("WorkflowStatus")
                            .relatedEntityId(perm.getWorkflowStatus().getId())
                            .tenant(tenant)
                            .build();
                        itemTypeSetRole = itemTypeSetRoleRepository.save(itemTypeSetRole);
                    }
                    
                    statusOwner.put("itemTypeSetRoleId", itemTypeSetRole.getId());
                    if (itemTypeSetRole.getGrant() != null) {
                        statusOwner.put("grantId", itemTypeSetRole.getGrant().getId());
                        statusOwner.put("grantName", "Grant diretto");
                    }
                    if (itemTypeSetRole.getRoleTemplate() != null) {
                        statusOwner.put("roleTemplateId", itemTypeSetRole.getRoleTemplate().getId());
                        statusOwner.put("roleTemplateName", itemTypeSetRole.getRoleTemplate().getName());
                    }
                    statusOwner.put("assignmentType", itemTypeSetRole.getAssignmentType());
                    
                    // Aggiungi informazioni su Grant di progetto se disponibile
                    addProjectGrantInfo(statusOwner, itemTypeSetRole.getId(), projectId, tenant.getId());
                }
                
                // Calcola se ci sono effettivamente assegnazioni (ruoli custom o grant)
                boolean hasAssignments = !perm.getAssignedRoles().isEmpty();
                if (perm.getWorkflowStatus() != null) {
                    Optional<ItemTypeSetRole> roleOpt = itemTypeSetRoleRepository
                        .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                            itemTypeSet.getId(), "WorkflowStatus", perm.getWorkflowStatus().getId(), 
                            ItemTypeSetRoleType.STATUS_OWNERS, tenant.getId());
                    if (roleOpt.isPresent() && roleOpt.get().getGrant() != null) {
                        hasAssignments = true;
                    }
                }
                statusOwner.put("hasAssignments", hasAssignments);
                
                // Aggiungi i dati delle assegnazioni esistenti
                List<Map<String, Object>> assignedRoles = new ArrayList<>();
                for (Role role : perm.getAssignedRoles()) {
                    Map<String, Object> roleMap = new HashMap<>();
                    roleMap.put("id", role.getId());
                    roleMap.put("name", role.getName());
                    roleMap.put("description", role.getDescription());
                    assignedRoles.add(roleMap);
                }
                statusOwner.put("assignedRoles", assignedRoles);
                
                statusOwners.add(statusOwner);
            }
        }
        result.put("STATUS_OWNERS", statusOwners);
        
        // FieldOwner permissions
        List<Map<String, Object>> fieldOwners = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<FieldOwnerPermission> permissions = fieldOwnerPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (FieldOwnerPermission perm : permissions) {
                Map<String, Object> fieldOwner = new HashMap<>();
                fieldOwner.put("id", perm.getId());
                fieldOwner.put("name", "Field Owners");
                Map<String, Object> fieldConfigMap = new HashMap<>();
                // IMPORTANTE: Le permission sono ora associate al Field, non alla FieldConfiguration
                fieldConfigMap.put("id", perm.getField().getId());
                fieldConfigMap.put("name", perm.getField().getName());
                fieldConfigMap.put("fieldType", null); // Field non ha fieldType, potrebbe essere nella FieldConfiguration
                fieldOwner.put("fieldConfiguration", fieldConfigMap);
                
                Map<String, Object> itemTypeMap3 = new HashMap<>();
                itemTypeMap3.put("id", config.getItemType().getId());
                itemTypeMap3.put("name", config.getItemType().getName());
                fieldOwner.put("itemType", itemTypeMap3);
                fieldOwner.put("assignedRolesCount", perm.getAssignedRoles().size());
                
                // Trova l'ItemTypeSetRole corrispondente
                // Prima, trova la FieldConfiguration corrispondente al Field nel FieldSet
                Field field = perm.getField();
                FieldSet fieldSet = config.getFieldSet();
                FieldConfiguration fieldConfig = null;
                if (fieldSet != null && fieldSet.getFieldSetEntries() != null) {
                    for (FieldSetEntry entry : fieldSet.getFieldSetEntries()) {
                        if (entry.getFieldConfiguration() != null && 
                            entry.getFieldConfiguration().getField() != null &&
                            entry.getFieldConfiguration().getField().getId().equals(field.getId())) {
                            fieldConfig = entry.getFieldConfiguration();
                            break;
                        }
                    }
                }
                
                if (fieldConfig != null) {
                    Optional<ItemTypeSetRole> itemTypeSetRoleOpt = itemTypeSetRoleRepository
                        .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                            itemTypeSet.getId(), "FieldConfiguration", fieldConfig.getId(), 
                            ItemTypeSetRoleType.FIELD_OWNERS, tenant.getId());
                    
                    ItemTypeSetRole itemTypeSetRole;
                    if (itemTypeSetRoleOpt.isPresent()) {
                        itemTypeSetRole = itemTypeSetRoleOpt.get();
                    } else {
                        // Crea l'ItemTypeSetRole se non esiste
                        itemTypeSetRole = ItemTypeSetRole.builder()
                            .roleType(ItemTypeSetRoleType.FIELD_OWNERS)
                            .name("Field Owner for " + fieldConfig.getName())
                            .description("Field Owner role for FieldConfiguration: " + fieldConfig.getName())
                            .itemTypeSet(itemTypeSet)
                            .relatedEntityType("FieldConfiguration")
                            .relatedEntityId(fieldConfig.getId())
                            .tenant(tenant)
                            .build();
                        itemTypeSetRole = itemTypeSetRoleRepository.save(itemTypeSetRole);
                    }
                    
                    fieldOwner.put("itemTypeSetRoleId", itemTypeSetRole.getId());
                    if (itemTypeSetRole.getGrant() != null) {
                        fieldOwner.put("grantId", itemTypeSetRole.getGrant().getId());
                        fieldOwner.put("grantName", "Grant diretto");
                    }
                    if (itemTypeSetRole.getRoleTemplate() != null) {
                        fieldOwner.put("roleTemplateId", itemTypeSetRole.getRoleTemplate().getId());
                        fieldOwner.put("roleTemplateName", itemTypeSetRole.getRoleTemplate().getName());
                    }
                    fieldOwner.put("assignmentType", itemTypeSetRole.getAssignmentType());
                    
                    // Aggiungi informazioni su Grant di progetto se disponibile
                    addProjectGrantInfo(fieldOwner, itemTypeSetRole.getId(), projectId, tenant.getId());
                    
                    // Calcola se ci sono effettivamente assegnazioni (ruoli custom o grant)
                    boolean hasAssignments = !perm.getAssignedRoles().isEmpty();
                    if (itemTypeSetRole.getGrant() != null) {
                        hasAssignments = true;
                    }
                    fieldOwner.put("hasAssignments", hasAssignments);
                } else {
                    // Se fieldConfig è null, calcola solo in base ai ruoli custom
                    boolean hasAssignments = !perm.getAssignedRoles().isEmpty();
                    fieldOwner.put("hasAssignments", hasAssignments);
                }

                // Aggiungi i dati delle assegnazioni esistenti
                List<Map<String, Object>> assignedRoles = new ArrayList<>();
                for (Role role : perm.getAssignedRoles()) {
                    Map<String, Object> roleMap = new HashMap<>();
                    roleMap.put("id", role.getId());
                    roleMap.put("name", role.getName());
                    roleMap.put("description", role.getDescription());
                    assignedRoles.add(roleMap);
                }
                fieldOwner.put("assignedRoles", assignedRoles);
                
                fieldOwners.add(fieldOwner);
            }
        }
        result.put("FIELD_OWNERS", fieldOwners);
        
        // Creator permissions
        List<Map<String, Object>> creators = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<CreatorPermission> permissions = creatorPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (CreatorPermission perm : permissions) {
                Map<String, Object> creator = new HashMap<>();
                creator.put("id", perm.getId());
                creator.put("name", "Creators");
                Map<String, Object> workflowMap2 = new HashMap<>();
                workflowMap2.put("id", config.getWorkflow().getId());
                workflowMap2.put("name", config.getWorkflow().getName());
                creator.put("workflow", workflowMap2);
                
                Map<String, Object> itemTypeMap4 = new HashMap<>();
                itemTypeMap4.put("id", config.getItemType().getId());
                itemTypeMap4.put("name", config.getItemType().getName());
                creator.put("itemType", itemTypeMap4);
                creator.put("assignedRolesCount", perm.getAssignedRoles().size());
                
                // Trova o crea l'ItemTypeSetRole corrispondente per CREATORS
                if (config.getWorkflow() != null) {
                    Optional<ItemTypeSetRole> itemTypeSetRoleOpt = itemTypeSetRoleRepository
                        .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                            itemTypeSet.getId(), "Workflow", config.getWorkflow().getId(), 
                            ItemTypeSetRoleType.CREATORS, tenant.getId());
                    
                    ItemTypeSetRole itemTypeSetRole;
                    if (itemTypeSetRoleOpt.isPresent()) {
                        itemTypeSetRole = itemTypeSetRoleOpt.get();
                    } else {
                        // Crea l'ItemTypeSetRole se non esiste
                        itemTypeSetRole = ItemTypeSetRole.builder()
                            .roleType(ItemTypeSetRoleType.CREATORS)
                            .name("Creator for " + config.getWorkflow().getName())
                            .description("Creator role for Workflow: " + config.getWorkflow().getName())
                            .itemTypeSet(itemTypeSet)
                            .relatedEntityType("Workflow")
                            .relatedEntityId(config.getWorkflow().getId())
                            .tenant(tenant)
                            .build();
                        itemTypeSetRole = itemTypeSetRoleRepository.save(itemTypeSetRole);
                    }
                    
                    creator.put("itemTypeSetRoleId", itemTypeSetRole.getId());
                    if (itemTypeSetRole.getGrant() != null) {
                        creator.put("grantId", itemTypeSetRole.getGrant().getId());
                        creator.put("grantName", "Grant diretto");
                    }
                    if (itemTypeSetRole.getRoleTemplate() != null) {
                        creator.put("roleTemplateId", itemTypeSetRole.getRoleTemplate().getId());
                        creator.put("roleTemplateName", itemTypeSetRole.getRoleTemplate().getName());
                    }
                    creator.put("assignmentType", itemTypeSetRole.getAssignmentType());
                    
                    // Aggiungi informazioni su Grant di progetto se disponibile
                    addProjectGrantInfo(creator, itemTypeSetRole.getId(), projectId, tenant.getId());
                }
                
                // Calcola se ci sono effettivamente assegnazioni (ruoli custom o grant)
                boolean hasAssignments = !perm.getAssignedRoles().isEmpty();
                if (config.getWorkflow() != null) {
                    Optional<ItemTypeSetRole> roleOpt = itemTypeSetRoleRepository
                        .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                            itemTypeSet.getId(), "Workflow", config.getWorkflow().getId(), 
                            ItemTypeSetRoleType.CREATORS, tenant.getId());
                    if (roleOpt.isPresent() && roleOpt.get().getGrant() != null) {
                        hasAssignments = true;
                    }
                }
                creator.put("hasAssignments", hasAssignments);
                
                // Aggiungi i dati delle assegnazioni esistenti
                List<Map<String, Object>> assignedRoles = new ArrayList<>();
                for (Role role : perm.getAssignedRoles()) {
                    Map<String, Object> roleMap = new HashMap<>();
                    roleMap.put("id", role.getId());
                    roleMap.put("name", role.getName());
                    roleMap.put("description", role.getDescription());
                    assignedRoles.add(roleMap);
                }
                creator.put("assignedRoles", assignedRoles);
                
                creators.add(creator);
            }
        }
        result.put("CREATORS", creators);
        
        // Executor permissions
        List<Map<String, Object>> executors = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<ExecutorPermission> permissions = executorPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (ExecutorPermission perm : permissions) {
                Map<String, Object> executor = new HashMap<>();
                executor.put("id", perm.getId());
                executor.put("name", "Executors");
                Map<String, Object> transitionMap = new HashMap<>();
                transitionMap.put("id", perm.getTransition().getId());
                transitionMap.put("name", perm.getTransition().getName() != null ? perm.getTransition().getName() : "N/A");
                executor.put("transition", transitionMap);
                Map<String, Object> fromStatusMap = new HashMap<>();
                if (perm.getTransition().getFromStatus() != null && perm.getTransition().getFromStatus().getStatus() != null) {
                    fromStatusMap.put("id", perm.getTransition().getFromStatus().getId());
                    fromStatusMap.put("name", perm.getTransition().getFromStatus().getStatus().getName());
                } else {
                    fromStatusMap.put("id", null);
                    fromStatusMap.put("name", "N/A");
                }
                executor.put("fromStatus", fromStatusMap);
                
                Map<String, Object> toStatusMap = new HashMap<>();
                if (perm.getTransition().getToStatus() != null && perm.getTransition().getToStatus().getStatus() != null) {
                    toStatusMap.put("id", perm.getTransition().getToStatus().getId());
                    toStatusMap.put("name", perm.getTransition().getToStatus().getStatus().getName());
                } else {
                    toStatusMap.put("id", null);
                    toStatusMap.put("name", "N/A");
                }
                executor.put("toStatus", toStatusMap);
                Map<String, Object> workflowMap3 = new HashMap<>();
                workflowMap3.put("id", config.getWorkflow().getId());
                workflowMap3.put("name", config.getWorkflow().getName());
                executor.put("workflow", workflowMap3);
                
                Map<String, Object> itemTypeMap5 = new HashMap<>();
                itemTypeMap5.put("id", config.getItemType().getId());
                itemTypeMap5.put("name", config.getItemType().getName());
                executor.put("itemType", itemTypeMap5);
                executor.put("assignedRolesCount", perm.getAssignedRoles().size());
                
                // Trova o crea l'ItemTypeSetRole corrispondente per EXECUTORS
                if (perm.getTransition() != null) {
                    Optional<ItemTypeSetRole> itemTypeSetRoleOpt = itemTypeSetRoleRepository
                        .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                            itemTypeSet.getId(), "Transition", perm.getTransition().getId(), 
                            ItemTypeSetRoleType.EXECUTORS, tenant.getId());
                    
                    ItemTypeSetRole itemTypeSetRole;
                    if (itemTypeSetRoleOpt.isPresent()) {
                        itemTypeSetRole = itemTypeSetRoleOpt.get();
                    } else {
                        // Crea l'ItemTypeSetRole se non esiste
                        itemTypeSetRole = ItemTypeSetRole.builder()
                            .roleType(ItemTypeSetRoleType.EXECUTORS)
                            .name("Executor for " + (perm.getTransition().getName() != null ? perm.getTransition().getName() : "Transition " + perm.getTransition().getId()))
                            .description("Executor role for Transition: " + (perm.getTransition().getName() != null ? perm.getTransition().getName() : "N/A"))
                            .itemTypeSet(itemTypeSet)
                            .relatedEntityType("Transition")
                            .relatedEntityId(perm.getTransition().getId())
                            .tenant(tenant)
                            .build();
                        itemTypeSetRole = itemTypeSetRoleRepository.save(itemTypeSetRole);
                    }
                    
                    executor.put("itemTypeSetRoleId", itemTypeSetRole.getId());
                    if (itemTypeSetRole.getGrant() != null) {
                        executor.put("grantId", itemTypeSetRole.getGrant().getId());
                        executor.put("grantName", "Grant diretto");
                    }
                    if (itemTypeSetRole.getRoleTemplate() != null) {
                        executor.put("roleTemplateId", itemTypeSetRole.getRoleTemplate().getId());
                        executor.put("roleTemplateName", itemTypeSetRole.getRoleTemplate().getName());
                    }
                    executor.put("assignmentType", itemTypeSetRole.getAssignmentType());
                    
                    // Aggiungi informazioni su Grant di progetto se disponibile
                    addProjectGrantInfo(executor, itemTypeSetRole.getId(), projectId, tenant.getId());
                }
                
                // Calcola se ci sono effettivamente assegnazioni (ruoli custom o grant)
                boolean hasAssignments = !perm.getAssignedRoles().isEmpty();
                if (perm.getTransition() != null) {
                    Optional<ItemTypeSetRole> roleOpt = itemTypeSetRoleRepository
                        .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                            itemTypeSet.getId(), "Transition", perm.getTransition().getId(), 
                            ItemTypeSetRoleType.EXECUTORS, tenant.getId());
                    if (roleOpt.isPresent() && roleOpt.get().getGrant() != null) {
                        hasAssignments = true;
                    }
                }
                executor.put("hasAssignments", hasAssignments);
                
                // Aggiungi i dati delle assegnazioni esistenti
                List<Map<String, Object>> assignedRoles = new ArrayList<>();
                for (Role role : perm.getAssignedRoles()) {
                    Map<String, Object> roleMap = new HashMap<>();
                    roleMap.put("id", role.getId());
                    roleMap.put("name", role.getName());
                    roleMap.put("description", role.getDescription());
                    assignedRoles.add(roleMap);
                }
                executor.put("assignedRoles", assignedRoles);
                
                executors.add(executor);
            }
        }
        result.put("EXECUTORS", executors);
        
        // Editor permissions
        List<Map<String, Object>> editors = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<FieldStatusPermission> permissions = fieldStatusPermissionRepository
                .findAllByItemTypeConfigurationAndPermissionType(config, FieldStatusPermission.PermissionType.EDITORS);
            
            for (FieldStatusPermission perm : permissions) {
                Map<String, Object> editor = new HashMap<>();
                editor.put("id", perm.getId());
                editor.put("name", "Editors");
                Map<String, Object> fieldConfigMap2 = new HashMap<>();
                // IMPORTANTE: Le permission sono ora associate al Field, non alla FieldConfiguration
                fieldConfigMap2.put("id", perm.getField().getId());
                fieldConfigMap2.put("name", perm.getField().getName());
                editor.put("fieldConfiguration", fieldConfigMap2);
                
                Map<String, Object> workflowStatusMap2 = new HashMap<>();
                workflowStatusMap2.put("id", perm.getWorkflowStatus().getId());
                workflowStatusMap2.put("name", perm.getWorkflowStatus().getStatus().getName());
                editor.put("workflowStatus", workflowStatusMap2);
                
                Map<String, Object> itemTypeMap6 = new HashMap<>();
                itemTypeMap6.put("id", config.getItemType().getId());
                itemTypeMap6.put("name", config.getItemType().getName());
                editor.put("itemType", itemTypeMap6);
                editor.put("assignedRolesCount", perm.getAssignedRoles().size());
                
                // Trova o crea l'ItemTypeSetRole corrispondente per EDITORS
                // Prima, trova la FieldConfiguration corrispondente al Field nel FieldSet
                Field field = perm.getField();
                FieldSet fieldSet = config.getFieldSet();
                final FieldConfiguration fieldConfig = (fieldSet != null && fieldSet.getFieldSetEntries() != null) ?
                    fieldSet.getFieldSetEntries().stream()
                        .filter(entry -> entry.getFieldConfiguration() != null && 
                                        entry.getFieldConfiguration().getField() != null &&
                                        entry.getFieldConfiguration().getField().getId().equals(field.getId()))
                        .map(FieldSetEntry::getFieldConfiguration)
                        .findFirst()
                        .orElse(null) : null;
                
                if (fieldConfig != null && perm.getWorkflowStatus() != null) {
                    // Cerca l'ItemTypeSetRole che corrisponde a ItemTypeConfiguration + FieldConfiguration
                    List<ItemTypeSetRole> roles = itemTypeSetRoleRepository
                        .findRolesByItemTypeSetAndType(itemTypeSet.getId(), ItemTypeSetRoleType.EDITORS, tenant.getId());
                    
                    Optional<ItemTypeSetRole> itemTypeSetRoleOpt = roles.stream()
                        .filter(r -> r.getRelatedEntityType() != null &&
                                     r.getRelatedEntityType().equals("ItemTypeConfiguration") &&
                                     r.getRelatedEntityId() != null &&
                                     r.getRelatedEntityId().equals(config.getId()) &&
                                     r.getSecondaryEntityType() != null &&
                                     r.getSecondaryEntityType().equals("FieldConfiguration") &&
                                     r.getSecondaryEntityId() != null &&
                                     r.getSecondaryEntityId().equals(fieldConfig.getId()))
                        .findFirst();
                    
                    ItemTypeSetRole itemTypeSetRole;
                    if (itemTypeSetRoleOpt.isPresent()) {
                        itemTypeSetRole = itemTypeSetRoleOpt.get();
                    } else {
                        // Crea l'ItemTypeSetRole se non esiste
                        itemTypeSetRole = ItemTypeSetRole.builder()
                            .roleType(ItemTypeSetRoleType.EDITORS)
                            .name("Editor for " + fieldConfig.getName() + " in " + perm.getWorkflowStatus().getStatus().getName())
                            .description("Editor role for FieldConfiguration " + fieldConfig.getName() + " in WorkflowStatus " + perm.getWorkflowStatus().getStatus().getName())
                            .itemTypeSet(itemTypeSet)
                            .relatedEntityType("ItemTypeConfiguration")
                            .relatedEntityId(config.getId())
                            .secondaryEntityType("FieldConfiguration")
                            .secondaryEntityId(fieldConfig.getId())
                            .tenant(tenant)
                            .build();
                        itemTypeSetRole = itemTypeSetRoleRepository.save(itemTypeSetRole);
                    }
                    
                    editor.put("itemTypeSetRoleId", itemTypeSetRole.getId());
                    if (itemTypeSetRole.getGrant() != null) {
                        editor.put("grantId", itemTypeSetRole.getGrant().getId());
                        editor.put("grantName", "Grant diretto");
                    }
                    if (itemTypeSetRole.getRoleTemplate() != null) {
                        editor.put("roleTemplateId", itemTypeSetRole.getRoleTemplate().getId());
                        editor.put("roleTemplateName", itemTypeSetRole.getRoleTemplate().getName());
                    }
                    editor.put("assignmentType", itemTypeSetRole.getAssignmentType());
                    
                    // Aggiungi informazioni su Grant di progetto se disponibile
                    addProjectGrantInfo(editor, itemTypeSetRole.getId(), projectId, tenant.getId());
                }
                
                // Calcola se ci sono effettivamente assegnazioni (ruoli custom o grant)
                boolean hasAssignments = !perm.getAssignedRoles().isEmpty();
                if (fieldConfig != null && perm.getWorkflowStatus() != null) {
                    List<ItemTypeSetRole> roles = itemTypeSetRoleRepository
                        .findRolesByItemTypeSetAndType(itemTypeSet.getId(), ItemTypeSetRoleType.EDITORS, tenant.getId());
                    Optional<ItemTypeSetRole> roleOpt = roles.stream()
                        .filter(r -> r.getRelatedEntityType() != null &&
                                     r.getRelatedEntityType().equals("ItemTypeConfiguration") &&
                                     r.getRelatedEntityId() != null &&
                                     r.getRelatedEntityId().equals(config.getId()) &&
                                     r.getSecondaryEntityType() != null &&
                                     r.getSecondaryEntityType().equals("FieldConfiguration") &&
                                     r.getSecondaryEntityId() != null &&
                                     r.getSecondaryEntityId().equals(fieldConfig.getId()))
                        .findFirst();
                    if (roleOpt.isPresent() && roleOpt.get().getGrant() != null) {
                        hasAssignments = true;
                    }
                }
                editor.put("hasAssignments", hasAssignments);
                
                // Aggiungi i dati delle assegnazioni esistenti
                List<Map<String, Object>> assignedRoles = new ArrayList<>();
                for (Role role : perm.getAssignedRoles()) {
                    Map<String, Object> roleMap = new HashMap<>();
                    roleMap.put("id", role.getId());
                    roleMap.put("name", role.getName());
                    roleMap.put("description", role.getDescription());
                    assignedRoles.add(roleMap);
                }
                editor.put("assignedRoles", assignedRoles);
                
                editors.add(editor);
            }
        }
        result.put("EDITORS", editors);
        
        // Viewer permissions
        List<Map<String, Object>> viewers = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<FieldStatusPermission> permissions = fieldStatusPermissionRepository
                .findAllByItemTypeConfigurationAndPermissionType(config, FieldStatusPermission.PermissionType.VIEWERS);
            
            for (FieldStatusPermission perm : permissions) {
                Map<String, Object> viewer = new HashMap<>();
                viewer.put("id", perm.getId());
                viewer.put("name", "Viewers");
                Map<String, Object> fieldConfigMap3 = new HashMap<>();
                // IMPORTANTE: Le permission sono ora associate al Field, non alla FieldConfiguration
                fieldConfigMap3.put("id", perm.getField().getId());
                fieldConfigMap3.put("name", perm.getField().getName());
                viewer.put("fieldConfiguration", fieldConfigMap3);
                
                Map<String, Object> workflowStatusMap3 = new HashMap<>();
                workflowStatusMap3.put("id", perm.getWorkflowStatus().getId());
                workflowStatusMap3.put("name", perm.getWorkflowStatus().getStatus().getName());
                viewer.put("workflowStatus", workflowStatusMap3);
                
                Map<String, Object> itemTypeMap7 = new HashMap<>();
                itemTypeMap7.put("id", config.getItemType().getId());
                itemTypeMap7.put("name", config.getItemType().getName());
                viewer.put("itemType", itemTypeMap7);
                viewer.put("assignedRolesCount", perm.getAssignedRoles().size());
                
                // Trova o crea l'ItemTypeSetRole corrispondente per VIEWERS
                // Prima, trova la FieldConfiguration corrispondente al Field nel FieldSet
                Field field = perm.getField();
                FieldSet fieldSet = config.getFieldSet();
                final FieldConfiguration fieldConfig = (fieldSet != null && fieldSet.getFieldSetEntries() != null) ?
                    fieldSet.getFieldSetEntries().stream()
                        .filter(entry -> entry.getFieldConfiguration() != null && 
                                        entry.getFieldConfiguration().getField() != null &&
                                        entry.getFieldConfiguration().getField().getId().equals(field.getId()))
                        .map(FieldSetEntry::getFieldConfiguration)
                        .findFirst()
                        .orElse(null) : null;
                
                if (fieldConfig != null && perm.getWorkflowStatus() != null) {
                    // Cerca l'ItemTypeSetRole che corrisponde a ItemTypeConfiguration + FieldConfiguration
                    List<ItemTypeSetRole> roles = itemTypeSetRoleRepository
                        .findRolesByItemTypeSetAndType(itemTypeSet.getId(), ItemTypeSetRoleType.VIEWERS, tenant.getId());
                    
                    Optional<ItemTypeSetRole> itemTypeSetRoleOpt = roles.stream()
                        .filter(r -> r.getRelatedEntityType() != null &&
                                     r.getRelatedEntityType().equals("ItemTypeConfiguration") &&
                                     r.getRelatedEntityId() != null &&
                                     r.getRelatedEntityId().equals(config.getId()) &&
                                     r.getSecondaryEntityType() != null &&
                                     r.getSecondaryEntityType().equals("FieldConfiguration") &&
                                     r.getSecondaryEntityId() != null &&
                                     r.getSecondaryEntityId().equals(fieldConfig.getId()))
                        .findFirst();
                    
                    ItemTypeSetRole itemTypeSetRole;
                    if (itemTypeSetRoleOpt.isPresent()) {
                        itemTypeSetRole = itemTypeSetRoleOpt.get();
                    } else {
                        // Crea l'ItemTypeSetRole se non esiste
                        itemTypeSetRole = ItemTypeSetRole.builder()
                            .roleType(ItemTypeSetRoleType.VIEWERS)
                            .name("Viewer for " + fieldConfig.getName() + " in " + perm.getWorkflowStatus().getStatus().getName())
                            .description("Viewer role for FieldConfiguration " + fieldConfig.getName() + " in WorkflowStatus " + perm.getWorkflowStatus().getStatus().getName())
                            .itemTypeSet(itemTypeSet)
                            .relatedEntityType("ItemTypeConfiguration")
                            .relatedEntityId(config.getId())
                            .secondaryEntityType("FieldConfiguration")
                            .secondaryEntityId(fieldConfig.getId())
                            .tenant(tenant)
                            .build();
                        itemTypeSetRole = itemTypeSetRoleRepository.save(itemTypeSetRole);
                    }
                    
                    viewer.put("itemTypeSetRoleId", itemTypeSetRole.getId());
                    if (itemTypeSetRole.getGrant() != null) {
                        viewer.put("grantId", itemTypeSetRole.getGrant().getId());
                        viewer.put("grantName", "Grant diretto");
                    }
                    if (itemTypeSetRole.getRoleTemplate() != null) {
                        viewer.put("roleTemplateId", itemTypeSetRole.getRoleTemplate().getId());
                        viewer.put("roleTemplateName", itemTypeSetRole.getRoleTemplate().getName());
                    }
                    viewer.put("assignmentType", itemTypeSetRole.getAssignmentType());
                    
                    // Aggiungi informazioni su Grant di progetto se disponibile
                    addProjectGrantInfo(viewer, itemTypeSetRole.getId(), projectId, tenant.getId());
                }
                
                // Calcola se ci sono effettivamente assegnazioni (ruoli custom o grant)
                boolean hasAssignments = !perm.getAssignedRoles().isEmpty();
                if (fieldConfig != null && perm.getWorkflowStatus() != null) {
                    List<ItemTypeSetRole> roles = itemTypeSetRoleRepository
                        .findRolesByItemTypeSetAndType(itemTypeSet.getId(), ItemTypeSetRoleType.VIEWERS, tenant.getId());
                    Optional<ItemTypeSetRole> roleOpt = roles.stream()
                        .filter(r -> r.getRelatedEntityType() != null &&
                                     r.getRelatedEntityType().equals("ItemTypeConfiguration") &&
                                     r.getRelatedEntityId() != null &&
                                     r.getRelatedEntityId().equals(config.getId()) &&
                                     r.getSecondaryEntityType() != null &&
                                     r.getSecondaryEntityType().equals("FieldConfiguration") &&
                                     r.getSecondaryEntityId() != null &&
                                     r.getSecondaryEntityId().equals(fieldConfig.getId()))
                        .findFirst();
                    if (roleOpt.isPresent() && roleOpt.get().getGrant() != null) {
                        hasAssignments = true;
                    }
                }
                viewer.put("hasAssignments", hasAssignments);
                
                // Aggiungi i dati delle assegnazioni esistenti
                List<Map<String, Object>> assignedRoles = new ArrayList<>();
                for (Role role : perm.getAssignedRoles()) {
                    Map<String, Object> roleMap = new HashMap<>();
                    roleMap.put("id", role.getId());
                    roleMap.put("name", role.getName());
                    roleMap.put("description", role.getDescription());
                    assignedRoles.add(roleMap);
                }
                viewer.put("assignedRoles", assignedRoles);
                
                viewers.add(viewer);
            }
        }
        result.put("VIEWERS", viewers);
        
        return result;
        } catch (Exception e) {
            log.error("Error retrieving permissions", e);
            throw new ApiException("Error retrieving permissions: " + e.getMessage(), e);
        }
    }
    
    /**
     * Assegna un ruolo a una permission
     */
    public void assignRoleToPermission(Long permissionId, Long roleId, String permissionType) {
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new ApiException("Role not found"));
        
        // Trova la permission per ID e TIPO
        WorkerPermission workerPermission = null;
        StatusOwnerPermission statusOwnerPermission = null;
        FieldOwnerPermission fieldOwnerPermission = null;
        CreatorPermission creatorPermission = null;
        ExecutorPermission executorPermission = null;
        FieldStatusPermission fieldStatusPermission = null;
        
        if (permissionType == null) {
            throw new ApiException("Permission type must be provided");
        }
        
        // Cerca solo nella tabella specifica in base al tipo
        switch (permissionType) {
            case "WORKERS":
                workerPermission = workerPermissionRepository.findById(permissionId).orElse(null);
                if (workerPermission != null) {
                    workerPermission.getAssignedRoles().add(role);
                    workerPermissionRepository.save(workerPermission);
                    entityManager.flush();
                    entityManager.clear();
                    return;
                }
                break;
            case "STATUS_OWNERS":
                statusOwnerPermission = statusOwnerPermissionRepository.findById(permissionId).orElse(null);
                if (statusOwnerPermission != null) {
                    statusOwnerPermission.getAssignedRoles().add(role);
                    statusOwnerPermissionRepository.save(statusOwnerPermission);
                    entityManager.flush();
                    entityManager.clear();
                    return;
                }
                break;
            case "FIELD_OWNERS":
                fieldOwnerPermission = fieldOwnerPermissionRepository.findById(permissionId).orElse(null);
                if (fieldOwnerPermission != null) {
                    fieldOwnerPermission.getAssignedRoles().add(role);
                    fieldOwnerPermissionRepository.save(fieldOwnerPermission);
                    entityManager.flush();
                    entityManager.clear();
                    return;
                }
                break;
            case "CREATORS":
                creatorPermission = creatorPermissionRepository.findById(permissionId).orElse(null);
                if (creatorPermission != null) {
                    creatorPermission.getAssignedRoles().add(role);
                    creatorPermissionRepository.save(creatorPermission);
                    entityManager.flush();
                    entityManager.clear();
                    return;
                }
                break;
            case "EXECUTORS":
                executorPermission = executorPermissionRepository.findById(permissionId).orElse(null);
                if (executorPermission != null) {
                    executorPermission.getAssignedRoles().add(role);
                    executorPermissionRepository.save(executorPermission);
                    entityManager.flush();
                    entityManager.clear();
                    return;
                }
                break;
            case "EDITORS":
            case "VIEWERS":
                fieldStatusPermission = fieldStatusPermissionRepository.findById(permissionId).orElse(null);
                if (fieldStatusPermission != null) {
                    fieldStatusPermission.getAssignedRoles().add(role);
                    fieldStatusPermissionRepository.save(fieldStatusPermission);
                    entityManager.flush();
                    entityManager.clear();
                    return;
                }
                break;
            default:
                throw new ApiException("Unknown permission type: " + permissionType);
        }
        
        throw new ApiException("Permission not found with ID: " + permissionId + " and type: " + permissionType);
    }
    
    /**
     * Rimuove un ruolo da una permission
     */
    public void removeRoleFromPermission(Long permissionId, Long roleId, String permissionType) {
        if (permissionType == null) {
            throw new ApiException("Permission type must be provided");
        }
        
        // Cerca solo nella tabella specifica in base al tipo
        switch (permissionType) {
            case "WORKERS":
                WorkerPermission workerPermission = workerPermissionRepository.findById(permissionId).orElse(null);
                if (workerPermission != null) {
                    workerPermission.getAssignedRoles().removeIf(role -> role.getId().equals(roleId));
                    workerPermissionRepository.save(workerPermission);
                    entityManager.flush();
                    entityManager.clear();
                    return;
                }
                break;
            case "STATUS_OWNERS":
                StatusOwnerPermission statusOwnerPermission = statusOwnerPermissionRepository.findById(permissionId).orElse(null);
                if (statusOwnerPermission != null) {
                    statusOwnerPermission.getAssignedRoles().removeIf(role -> role.getId().equals(roleId));
                    statusOwnerPermissionRepository.save(statusOwnerPermission);
                    entityManager.flush();
                    entityManager.clear();
                    return;
                }
                break;
            case "FIELD_OWNERS":
                FieldOwnerPermission fieldOwnerPermission = fieldOwnerPermissionRepository.findById(permissionId).orElse(null);
                if (fieldOwnerPermission != null) {
                    fieldOwnerPermission.getAssignedRoles().removeIf(role -> role.getId().equals(roleId));
                    fieldOwnerPermissionRepository.save(fieldOwnerPermission);
                    entityManager.flush();
                    entityManager.clear();
                    return;
                }
                break;
            case "CREATORS":
                CreatorPermission creatorPermission = creatorPermissionRepository.findById(permissionId).orElse(null);
                if (creatorPermission != null) {
                    creatorPermission.getAssignedRoles().removeIf(role -> role.getId().equals(roleId));
                    creatorPermissionRepository.save(creatorPermission);
                    entityManager.flush();
                    entityManager.clear();
                    return;
                }
                break;
            case "EXECUTORS":
                ExecutorPermission executorPermission = executorPermissionRepository.findById(permissionId).orElse(null);
                if (executorPermission != null) {
                    executorPermission.getAssignedRoles().removeIf(role -> role.getId().equals(roleId));
                    executorPermissionRepository.save(executorPermission);
                    entityManager.flush();
                    entityManager.clear();
                    return;
                }
                break;
            case "EDITORS":
            case "VIEWERS":
                FieldStatusPermission fieldStatusPermission = fieldStatusPermissionRepository.findById(permissionId).orElse(null);
                if (fieldStatusPermission != null) {
                    fieldStatusPermission.getAssignedRoles().removeIf(role -> role.getId().equals(roleId));
                    fieldStatusPermissionRepository.save(fieldStatusPermission);
                    entityManager.flush();
                    entityManager.clear();
                    return;
                }
                break;
            default:
                throw new ApiException("Unknown permission type: " + permissionType);
        }
        
        throw new ApiException("Permission not found with ID: " + permissionId + " and type: " + permissionType);
    }
    
    /**
     * Helper method per aggiungere informazioni su Grant di progetto a una permission map
     */
    private void addProjectGrantInfo(Map<String, Object> permissionMap, Long itemTypeSetRoleId, Long projectId, Long tenantId) {
        if (projectId != null) {
            boolean hasProjectGrant = projectItemTypeSetRoleGrantRepository.existsByItemTypeSetRoleIdAndProjectIdAndTenantId(
                    itemTypeSetRoleId, projectId, tenantId);
            permissionMap.put("hasProjectGrant", hasProjectGrant);
            if (hasProjectGrant) {
                Optional<ProjectItemTypeSetRoleGrant> projectGrantOpt = projectItemTypeSetRoleGrantRepository
                        .findByItemTypeSetRoleIdAndProjectIdAndTenantId(itemTypeSetRoleId, projectId, tenantId);
                if (projectGrantOpt.isPresent()) {
                    permissionMap.put("projectGrantId", projectGrantOpt.get().getGrant().getId());
                }
            }
        }
    }
}
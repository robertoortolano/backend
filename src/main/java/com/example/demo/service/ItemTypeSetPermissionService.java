package com.example.demo.service;

import com.example.demo.entity.*;
// RIMOSSO: ItemTypeSetRoleType - ItemTypeSetRole eliminata
import com.example.demo.exception.ApiException;
import com.example.demo.repository.*;
// RIMOSSO: ProjectItemTypeSetRoleRole eliminata
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
    
    // Servizi per PermissionAssignment (nuova struttura)
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;
    
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
                worker.put("permissionType", "WorkerPermission"); // Aggiunto per il frontend
                Map<String, Object> itemTypeMap = new HashMap<>();
                itemTypeMap.put("id", config.getItemType().getId());
                itemTypeMap.put("name", config.getItemType().getName());
                worker.put("itemType", itemTypeMap);
                // Recupera PermissionAssignment invece di ItemTypeSetRole
                Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                        "WorkerPermission", perm.getId(), tenant);
                
                Set<Role> assignedRolesSet = assignmentOpt.map(PermissionAssignment::getRoles)
                        .orElse(new HashSet<>());
                worker.put("assignedRolesCount", assignedRolesSet.size());
                
                // Aggiungi informazioni su Grant se presente
                if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                    Grant grant = assignmentOpt.get().getGrant();
                    worker.put("grantId", grant.getId());
                    worker.put("grantName", "Grant diretto");
                }
                
                // Aggiungi informazioni su Grant di progetto se disponibile
                if (projectId != null) {
                    Optional<ProjectPermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                            "WorkerPermission", perm.getId(), projectId, tenant);
                    if (projectAssignmentOpt.isPresent() && projectAssignmentOpt.get().getAssignment().getGrant() != null) {
                        Grant projectGrant = projectAssignmentOpt.get().getAssignment().getGrant();
                        worker.put("projectGrantId", projectGrant.getId());
                        worker.put("projectGrantName", "Grant di progetto");
                    }
                }
                
                // Calcola se ci sono effettivamente assegnazioni (ruoli custom o grant)
                boolean hasAssignments = !assignedRolesSet.isEmpty();
                if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                    hasAssignments = true;
                }
                worker.put("hasAssignments", hasAssignments);
                
                // Aggiungi i dati delle assegnazioni esistenti
                List<Map<String, Object>> assignedRoles = new ArrayList<>();
                for (Role role : assignedRolesSet) {
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
        result.put("Workers", workers); // Chiave aggiornata: usa nome completo invece di "WORKERS"
        
        // StatusOwner permissions
        List<Map<String, Object>> statusOwners = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<StatusOwnerPermission> permissions = statusOwnerPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (StatusOwnerPermission perm : permissions) {
                Map<String, Object> statusOwner = new HashMap<>();
                statusOwner.put("id", perm.getId());
                statusOwner.put("name", "Status Owners");
                statusOwner.put("permissionType", "StatusOwnerPermission"); // Aggiunto per il frontend
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
                // Recupera PermissionAssignment invece di ItemTypeSetRole
                Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                        "StatusOwnerPermission", perm.getId(), tenant);
                
                Set<Role> assignedRolesSet = assignmentOpt.map(PermissionAssignment::getRoles)
                        .orElse(new HashSet<>());
                statusOwner.put("assignedRolesCount", assignedRolesSet.size());
                
                // Aggiungi informazioni su Grant se presente
                if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                    Grant grant = assignmentOpt.get().getGrant();
                    statusOwner.put("grantId", grant.getId());
                    statusOwner.put("grantName", "Grant diretto");
                }
                
                // Aggiungi informazioni su Grant di progetto se disponibile
                if (projectId != null) {
                    Optional<ProjectPermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                            "StatusOwnerPermission", perm.getId(), projectId, tenant);
                    if (projectAssignmentOpt.isPresent() && projectAssignmentOpt.get().getAssignment().getGrant() != null) {
                        Grant projectGrant = projectAssignmentOpt.get().getAssignment().getGrant();
                        statusOwner.put("projectGrantId", projectGrant.getId());
                        statusOwner.put("projectGrantName", "Grant di progetto");
                    }
                    // Aggiungi ruoli di progetto
                    if (projectAssignmentOpt.isPresent()) {
                        Set<Role> projectRoles = projectAssignmentOpt.get().getAssignment().getRoles();
                        List<Map<String, Object>> projectRolesList = new ArrayList<>();
                        for (Role role : projectRoles) {
                            Map<String, Object> roleMap = new HashMap<>();
                            roleMap.put("id", role.getId());
                            roleMap.put("name", role.getName());
                            roleMap.put("description", role.getDescription());
                            projectRolesList.add(roleMap);
                        }
                        statusOwner.put("projectAssignedRoles", projectRolesList);
                        statusOwner.put("hasProjectRoles", !projectRoles.isEmpty());
                    }
                }
                
                // Calcola se ci sono effettivamente assegnazioni (ruoli custom o grant)
                boolean hasAssignments = !assignedRolesSet.isEmpty();
                if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                    hasAssignments = true;
                }
                statusOwner.put("hasAssignments", hasAssignments);
                
                // Aggiungi i dati delle assegnazioni esistenti
                List<Map<String, Object>> assignedRoles = new ArrayList<>();
                for (Role role : assignedRolesSet) {
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
        result.put("Status Owners", statusOwners); // Chiave aggiornata: usa nome completo invece di "STATUS_OWNERS"
        
        // FieldOwner permissions
        List<Map<String, Object>> fieldOwners = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<FieldOwnerPermission> permissions = fieldOwnerPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (FieldOwnerPermission perm : permissions) {
                Map<String, Object> fieldOwner = new HashMap<>();
                fieldOwner.put("id", perm.getId());
                fieldOwner.put("name", "Field Owners");
                fieldOwner.put("permissionType", "FieldOwnerPermission"); // Aggiunto per il frontend
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
                // Recupera PermissionAssignment invece di ItemTypeSetRole
                Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                        "FieldOwnerPermission", perm.getId(), tenant);
                
                Set<Role> assignedRolesSet = assignmentOpt.map(PermissionAssignment::getRoles)
                        .orElse(new HashSet<>());
                fieldOwner.put("assignedRolesCount", assignedRolesSet.size());
                
                // Aggiungi informazioni su Grant se presente
                if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                    Grant grant = assignmentOpt.get().getGrant();
                    fieldOwner.put("grantId", grant.getId());
                    fieldOwner.put("grantName", "Grant diretto");
                }
                
                // Aggiungi informazioni su Grant di progetto se disponibile
                if (projectId != null) {
                    Optional<ProjectPermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                            "FieldOwnerPermission", perm.getId(), projectId, tenant);
                    if (projectAssignmentOpt.isPresent() && projectAssignmentOpt.get().getAssignment().getGrant() != null) {
                        Grant projectGrant = projectAssignmentOpt.get().getAssignment().getGrant();
                        fieldOwner.put("projectGrantId", projectGrant.getId());
                        fieldOwner.put("projectGrantName", "Grant di progetto");
                    }
                    // Aggiungi ruoli di progetto
                    if (projectAssignmentOpt.isPresent()) {
                        Set<Role> projectRoles = projectAssignmentOpt.get().getAssignment().getRoles();
                        List<Map<String, Object>> projectRolesList = new ArrayList<>();
                        for (Role role : projectRoles) {
                            Map<String, Object> roleMap = new HashMap<>();
                            roleMap.put("id", role.getId());
                            roleMap.put("name", role.getName());
                            roleMap.put("description", role.getDescription());
                            projectRolesList.add(roleMap);
                        }
                        fieldOwner.put("projectAssignedRoles", projectRolesList);
                        fieldOwner.put("hasProjectRoles", !projectRoles.isEmpty());
                    }
                }
                
                // Calcola se ci sono effettivamente assegnazioni (ruoli custom o grant)
                boolean hasAssignments = !assignedRolesSet.isEmpty();
                if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                    hasAssignments = true;
                }
                fieldOwner.put("hasAssignments", hasAssignments);

                // Aggiungi i dati delle assegnazioni esistenti
                List<Map<String, Object>> assignedRoles = new ArrayList<>();
                for (Role role : assignedRolesSet) {
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
        result.put("Field Owners", fieldOwners); // Chiave aggiornata: usa nome completo invece di "FIELD_OWNERS"
        
        // Creator permissions
        List<Map<String, Object>> creators = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<CreatorPermission> permissions = creatorPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (CreatorPermission perm : permissions) {
                Map<String, Object> creator = new HashMap<>();
                creator.put("id", perm.getId());
                creator.put("name", "Creators");
                creator.put("permissionType", "CreatorPermission"); // Aggiunto per il frontend
                Map<String, Object> workflowMap2 = new HashMap<>();
                workflowMap2.put("id", config.getWorkflow().getId());
                workflowMap2.put("name", config.getWorkflow().getName());
                creator.put("workflow", workflowMap2);
                
                Map<String, Object> itemTypeMap4 = new HashMap<>();
                itemTypeMap4.put("id", config.getItemType().getId());
                itemTypeMap4.put("name", config.getItemType().getName());
                creator.put("itemType", itemTypeMap4);
                // Recupera PermissionAssignment invece di ItemTypeSetRole
                Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                        "CreatorPermission", perm.getId(), tenant);
                
                Set<Role> assignedRolesSet = assignmentOpt.map(PermissionAssignment::getRoles)
                        .orElse(new HashSet<>());
                creator.put("assignedRolesCount", assignedRolesSet.size());
                
                // Aggiungi informazioni su Grant se presente
                if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                    Grant grant = assignmentOpt.get().getGrant();
                    creator.put("grantId", grant.getId());
                    creator.put("grantName", "Grant diretto");
                }
                
                // Aggiungi informazioni su Grant di progetto se disponibile
                if (projectId != null) {
                    Optional<ProjectPermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                            "CreatorPermission", perm.getId(), projectId, tenant);
                    if (projectAssignmentOpt.isPresent() && projectAssignmentOpt.get().getAssignment().getGrant() != null) {
                        Grant projectGrant = projectAssignmentOpt.get().getAssignment().getGrant();
                        creator.put("projectGrantId", projectGrant.getId());
                        creator.put("projectGrantName", "Grant di progetto");
                    }
                    // Aggiungi ruoli di progetto
                    if (projectAssignmentOpt.isPresent()) {
                        Set<Role> projectRoles = projectAssignmentOpt.get().getAssignment().getRoles();
                        List<Map<String, Object>> projectRolesList = new ArrayList<>();
                        for (Role role : projectRoles) {
                            Map<String, Object> roleMap = new HashMap<>();
                            roleMap.put("id", role.getId());
                            roleMap.put("name", role.getName());
                            roleMap.put("description", role.getDescription());
                            projectRolesList.add(roleMap);
                        }
                        creator.put("projectAssignedRoles", projectRolesList);
                        creator.put("hasProjectRoles", !projectRoles.isEmpty());
                    }
                }
                
                // Calcola se ci sono effettivamente assegnazioni (ruoli custom o grant)
                boolean hasAssignments = !assignedRolesSet.isEmpty();
                if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                    hasAssignments = true;
                }
                creator.put("hasAssignments", hasAssignments);
                
                // Aggiungi i dati delle assegnazioni esistenti
                List<Map<String, Object>> assignedRoles = new ArrayList<>();
                for (Role role : assignedRolesSet) {
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
        result.put("Creators", creators); // Chiave aggiornata: usa nome completo invece di "CREATORS"
        
        // Executor permissions
        List<Map<String, Object>> executors = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<ExecutorPermission> permissions = executorPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (ExecutorPermission perm : permissions) {
                Map<String, Object> executor = new HashMap<>();
                executor.put("id", perm.getId());
                executor.put("name", "Executors");
                executor.put("permissionType", "ExecutorPermission"); // Aggiunto per il frontend
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
                // Recupera PermissionAssignment invece di ItemTypeSetRole
                Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                        "ExecutorPermission", perm.getId(), tenant);
                
                Set<Role> assignedRolesSet = assignmentOpt.map(PermissionAssignment::getRoles)
                        .orElse(new HashSet<>());
                executor.put("assignedRolesCount", assignedRolesSet.size());
                
                // Aggiungi informazioni su Grant se presente
                if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                    Grant grant = assignmentOpt.get().getGrant();
                    executor.put("grantId", grant.getId());
                    executor.put("grantName", "Grant diretto");
                }
                
                // Aggiungi informazioni su Grant di progetto se disponibile
                if (projectId != null) {
                    Optional<ProjectPermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                            "ExecutorPermission", perm.getId(), projectId, tenant);
                    if (projectAssignmentOpt.isPresent() && projectAssignmentOpt.get().getAssignment().getGrant() != null) {
                        Grant projectGrant = projectAssignmentOpt.get().getAssignment().getGrant();
                        executor.put("projectGrantId", projectGrant.getId());
                        executor.put("projectGrantName", "Grant di progetto");
                    }
                    // Aggiungi ruoli di progetto
                    if (projectAssignmentOpt.isPresent()) {
                        Set<Role> projectRoles = projectAssignmentOpt.get().getAssignment().getRoles();
                        List<Map<String, Object>> projectRolesList = new ArrayList<>();
                        for (Role role : projectRoles) {
                            Map<String, Object> roleMap = new HashMap<>();
                            roleMap.put("id", role.getId());
                            roleMap.put("name", role.getName());
                            roleMap.put("description", role.getDescription());
                            projectRolesList.add(roleMap);
                        }
                        executor.put("projectAssignedRoles", projectRolesList);
                        executor.put("hasProjectRoles", !projectRoles.isEmpty());
                    }
                }
                
                // Calcola se ci sono effettivamente assegnazioni (ruoli custom o grant)
                boolean hasAssignments = !assignedRolesSet.isEmpty();
                if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                    hasAssignments = true;
                }
                executor.put("hasAssignments", hasAssignments);
                
                // Aggiungi i dati delle assegnazioni esistenti
                List<Map<String, Object>> assignedRoles = new ArrayList<>();
                for (Role role : assignedRolesSet) {
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
        result.put("Executors", executors); // Chiave aggiornata: usa nome completo invece di "EXECUTORS"
        
        // Editor permissions
        List<Map<String, Object>> editors = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<FieldStatusPermission> permissions = fieldStatusPermissionRepository
                .findAllByItemTypeConfigurationAndPermissionType(config, FieldStatusPermission.PermissionType.EDITORS);
            
            for (FieldStatusPermission perm : permissions) {
                Map<String, Object> editor = new HashMap<>();
                editor.put("id", perm.getId());
                editor.put("name", "Editors");
                editor.put("permissionType", "FieldStatusPermission"); // Aggiunto per il frontend
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
                // Recupera PermissionAssignment invece di ItemTypeSetRole
                Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                        "FieldStatusPermission", perm.getId(), tenant);
                
                Set<Role> assignedRolesSet = assignmentOpt.map(PermissionAssignment::getRoles)
                        .orElse(new HashSet<>());
                editor.put("assignedRolesCount", assignedRolesSet.size());
                
                // Aggiungi informazioni su Grant se presente
                if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                    Grant grant = assignmentOpt.get().getGrant();
                    editor.put("grantId", grant.getId());
                    editor.put("grantName", "Grant diretto");
                }
                
                // Aggiungi informazioni su Grant di progetto se disponibile
                if (projectId != null) {
                    Optional<ProjectPermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                            "FieldStatusPermission", perm.getId(), projectId, tenant);
                    if (projectAssignmentOpt.isPresent() && projectAssignmentOpt.get().getAssignment().getGrant() != null) {
                        Grant projectGrant = projectAssignmentOpt.get().getAssignment().getGrant();
                        editor.put("projectGrantId", projectGrant.getId());
                        editor.put("projectGrantName", "Grant di progetto");
                    }
                    // Aggiungi ruoli di progetto
                    if (projectAssignmentOpt.isPresent()) {
                        Set<Role> projectRoles = projectAssignmentOpt.get().getAssignment().getRoles();
                        List<Map<String, Object>> projectRolesList = new ArrayList<>();
                        for (Role role : projectRoles) {
                            Map<String, Object> roleMap = new HashMap<>();
                            roleMap.put("id", role.getId());
                            roleMap.put("name", role.getName());
                            roleMap.put("description", role.getDescription());
                            projectRolesList.add(roleMap);
                        }
                        editor.put("projectAssignedRoles", projectRolesList);
                        editor.put("hasProjectRoles", !projectRoles.isEmpty());
                    }
                }
                
                // Calcola se ci sono effettivamente assegnazioni (ruoli custom o grant)
                boolean hasAssignments = !assignedRolesSet.isEmpty();
                if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                    hasAssignments = true;
                }
                editor.put("hasAssignments", hasAssignments);
                
                // Aggiungi i dati delle assegnazioni esistenti
                List<Map<String, Object>> assignedRoles = new ArrayList<>();
                for (Role role : assignedRolesSet) {
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
        result.put("Editors", editors); // Chiave aggiornata: usa nome completo invece di "EDITORS"
        
        // Viewer permissions
        List<Map<String, Object>> viewers = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<FieldStatusPermission> permissions = fieldStatusPermissionRepository
                .findAllByItemTypeConfigurationAndPermissionType(config, FieldStatusPermission.PermissionType.VIEWERS);
            
            for (FieldStatusPermission perm : permissions) {
                Map<String, Object> viewer = new HashMap<>();
                viewer.put("id", perm.getId());
                viewer.put("name", "Viewers");
                viewer.put("permissionType", "FieldStatusPermission"); // Aggiunto per il frontend
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
                // Recupera PermissionAssignment invece di ItemTypeSetRole
                Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(
                        "FieldStatusPermission", perm.getId(), tenant);
                
                Set<Role> assignedRolesSet = assignmentOpt.map(PermissionAssignment::getRoles)
                        .orElse(new HashSet<>());
                viewer.put("assignedRolesCount", assignedRolesSet.size());
                
                // Aggiungi informazioni su Grant se presente
                if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                    Grant grant = assignmentOpt.get().getGrant();
                    viewer.put("grantId", grant.getId());
                    viewer.put("grantName", "Grant diretto");
                }
                
                // Aggiungi informazioni su Grant di progetto se disponibile
                if (projectId != null) {
                    Optional<ProjectPermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                            "FieldStatusPermission", perm.getId(), projectId, tenant);
                    if (projectAssignmentOpt.isPresent() && projectAssignmentOpt.get().getAssignment().getGrant() != null) {
                        Grant projectGrant = projectAssignmentOpt.get().getAssignment().getGrant();
                        viewer.put("projectGrantId", projectGrant.getId());
                        viewer.put("projectGrantName", "Grant di progetto");
                    }
                    // Aggiungi ruoli di progetto
                    if (projectAssignmentOpt.isPresent()) {
                        Set<Role> projectRoles = projectAssignmentOpt.get().getAssignment().getRoles();
                        List<Map<String, Object>> projectRolesList = new ArrayList<>();
                        for (Role role : projectRoles) {
                            Map<String, Object> roleMap = new HashMap<>();
                            roleMap.put("id", role.getId());
                            roleMap.put("name", role.getName());
                            roleMap.put("description", role.getDescription());
                            projectRolesList.add(roleMap);
                        }
                        viewer.put("projectAssignedRoles", projectRolesList);
                        viewer.put("hasProjectRoles", !projectRoles.isEmpty());
                    }
                }
                
                // Calcola se ci sono effettivamente assegnazioni (ruoli custom o grant)
                boolean hasAssignments = !assignedRolesSet.isEmpty();
                if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
                    hasAssignments = true;
                }
                viewer.put("hasAssignments", hasAssignments);
                
                // Aggiungi i dati delle assegnazioni esistenti
                List<Map<String, Object>> assignedRoles = new ArrayList<>();
                for (Role role : assignedRolesSet) {
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
        result.put("Viewers", viewers); // Chiave aggiornata: usa nome completo invece di "VIEWERS"
        
        return result;
        } catch (Exception e) {
            log.error("Error retrieving permissions", e);
            throw new ApiException("Error retrieving permissions: " + e.getMessage(), e);
        }
    }
    
    /**
     * Assegna un ruolo a una permission
     */
    public void assignRoleToPermission(Long permissionId, Long roleId, String permissionType, Tenant tenant) {
        if (permissionType == null) {
            throw new ApiException("Permission type must be provided");
        }
        
        // Valida che il tipo di permission sia uno dei tipi supportati
        // Dopo la migrazione, accettiamo solo i nuovi tipi
        switch (permissionType) {
            case "WorkerPermission", "StatusOwnerPermission", "FieldOwnerPermission",
                 "CreatorPermission", "ExecutorPermission", "FieldStatusPermission" -> {
                // Tipo valido, procedi
            }
            default -> throw new ApiException("Unknown permission type: " + permissionType + 
                ". Supported types: WorkerPermission, StatusOwnerPermission, FieldOwnerPermission, " +
                "CreatorPermission, ExecutorPermission, FieldStatusPermission");
        }
        
        // Usa PermissionAssignmentService per aggiungere il ruolo
        permissionAssignmentService.addRole(permissionType, permissionId, roleId, tenant);
    }
    
    /**
     * Rimuove un ruolo da una permission
     */
    public void removeRoleFromPermission(Long permissionId, Long roleId, String permissionType, Tenant tenant) {
        if (permissionType == null) {
            throw new ApiException("Permission type must be provided");
        }
        
        // Valida che il tipo di permission sia uno dei tipi supportati
        // Dopo la migrazione, accettiamo solo i nuovi tipi
        switch (permissionType) {
            case "WorkerPermission", "StatusOwnerPermission", "FieldOwnerPermission",
                 "CreatorPermission", "ExecutorPermission", "FieldStatusPermission" -> {
                // Tipo valido, procedi
            }
            default -> throw new ApiException("Unknown permission type: " + permissionType + 
                ". Supported types: WorkerPermission, StatusOwnerPermission, FieldOwnerPermission, " +
                "CreatorPermission, ExecutorPermission, FieldStatusPermission");
        }
        
        // Usa PermissionAssignmentService per rimuovere il ruolo
        permissionAssignmentService.removeRole(permissionType, permissionId, roleId, tenant);
    }
    
    // RIMOSSO: Metodi obsoleti - ItemTypeSetRole e ProjectItemTypeSetRoleGrant/ProjectItemTypeSetRoleRole eliminate
    // Le grant di progetto sono ora gestite tramite ProjectPermissionAssignmentService
}
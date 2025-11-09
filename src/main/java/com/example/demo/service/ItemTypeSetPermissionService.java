package com.example.demo.service;

import com.example.demo.entity.*;
// RIMOSSO: ItemTypeSetRoleType - ItemTypeSetRole eliminata
import com.example.demo.exception.ApiException;
import com.example.demo.repository.*;
// RIMOSSO: ProjectItemTypeSetRoleRole eliminata
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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
            
            List<ItemTypeConfiguration> configurations = new ArrayList<>(itemTypeSet.getItemTypeConfigurations());
            Set<Long> configurationIds = configurations.stream()
                    .map(ItemTypeConfiguration::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            
            Map<Long, List<WorkerPermission>> workerPermissionsByConfigId = Collections.emptyMap();
            Set<Long> workerPermissionIds = Collections.emptySet();
            if (!configurationIds.isEmpty()) {
                List<WorkerPermission> workerPermissions = workerPermissionRepository
                        .findAllByItemTypeConfigurationIdInAndTenant(configurationIds, tenant);
                workerPermissionsByConfigId = workerPermissions.stream()
                        .collect(Collectors.groupingBy(perm -> perm.getItemTypeConfiguration().getId()));
                workerPermissionIds = workerPermissions.stream()
                        .map(WorkerPermission::getId)
                        .collect(Collectors.toSet());
            }
        
        // Worker permissions
        List<Map<String, Object>> workers = new ArrayList<>();
            Map<Long, List<StatusOwnerPermission>> statusPermissionsByConfigId = Collections.emptyMap();
            Set<Long> statusPermissionIds = Collections.emptySet();
            if (!configurationIds.isEmpty()) {
                List<StatusOwnerPermission> statusPermissions = statusOwnerPermissionRepository
                        .findAllByItemTypeConfigurationIdInAndTenant(configurationIds, tenant);
                statusPermissionsByConfigId = statusPermissions.stream()
                        .collect(Collectors.groupingBy(perm -> perm.getItemTypeConfiguration().getId()));
                statusPermissionIds = statusPermissions.stream()
                        .map(StatusOwnerPermission::getId)
                        .collect(Collectors.toSet());
            }
            
            Map<Long, List<FieldOwnerPermission>> fieldOwnerPermissionsByConfigId = Collections.emptyMap();
            Set<Long> fieldOwnerPermissionIds = Collections.emptySet();
            if (!configurationIds.isEmpty()) {
                List<FieldOwnerPermission> fieldOwnerPermissions = fieldOwnerPermissionRepository
                        .findAllByItemTypeConfigurationIdInAndTenant(configurationIds, tenant);
                fieldOwnerPermissionsByConfigId = fieldOwnerPermissions.stream()
                        .collect(Collectors.groupingBy(perm -> perm.getItemTypeConfiguration().getId()));
                fieldOwnerPermissionIds = fieldOwnerPermissions.stream()
                        .map(FieldOwnerPermission::getId)
                        .collect(Collectors.toSet());
            }
            
            Map<Long, List<CreatorPermission>> creatorPermissionsByConfigId = Collections.emptyMap();
            Set<Long> creatorPermissionIds = Collections.emptySet();
            if (!configurationIds.isEmpty()) {
                List<CreatorPermission> creatorPermissions = creatorPermissionRepository
                        .findAllByItemTypeConfigurationIdInAndTenant(configurationIds, tenant);
                creatorPermissionsByConfigId = creatorPermissions.stream()
                        .collect(Collectors.groupingBy(perm -> perm.getItemTypeConfiguration().getId()));
                creatorPermissionIds = creatorPermissions.stream()
                        .map(CreatorPermission::getId)
                        .collect(Collectors.toSet());
            }
            
            Map<Long, List<ExecutorPermission>> executorPermissionsByConfigId = Collections.emptyMap();
            Set<Long> executorPermissionIds = Collections.emptySet();
            if (!configurationIds.isEmpty()) {
                List<ExecutorPermission> executorPermissions = executorPermissionRepository
                        .findAllByItemTypeConfigurationIdInAndTenant(configurationIds, tenant);
                executorPermissionsByConfigId = executorPermissions.stream()
                        .collect(Collectors.groupingBy(perm -> perm.getItemTypeConfiguration().getId()));
                executorPermissionIds = executorPermissions.stream()
                        .map(ExecutorPermission::getId)
                        .collect(Collectors.toSet());
            }
            
            Map<Long, List<FieldStatusPermission>> editorsByConfigId = Collections.emptyMap();
            Map<Long, List<FieldStatusPermission>> viewersByConfigId = Collections.emptyMap();
            Set<Long> editorPermissionIds = Collections.emptySet();
            Set<Long> viewerPermissionIds = Collections.emptySet();
            if (!configurationIds.isEmpty()) {
                List<FieldStatusPermission> fieldStatusPermissions = fieldStatusPermissionRepository
                        .findAllByItemTypeConfigurationIdInAndTenant(configurationIds, tenant);
                editorsByConfigId = fieldStatusPermissions.stream()
                        .filter(perm -> perm.getPermissionType() == FieldStatusPermission.PermissionType.EDITORS)
                        .collect(Collectors.groupingBy(perm -> perm.getItemTypeConfiguration().getId()));
                viewersByConfigId = fieldStatusPermissions.stream()
                        .filter(perm -> perm.getPermissionType() == FieldStatusPermission.PermissionType.VIEWERS)
                        .collect(Collectors.groupingBy(perm -> perm.getItemTypeConfiguration().getId()));
                editorPermissionIds = fieldStatusPermissions.stream()
                        .filter(perm -> perm.getPermissionType() == FieldStatusPermission.PermissionType.EDITORS)
                        .map(FieldStatusPermission::getId)
                        .collect(Collectors.toSet());
                viewerPermissionIds = fieldStatusPermissions.stream()
                        .filter(perm -> perm.getPermissionType() == FieldStatusPermission.PermissionType.VIEWERS)
                        .map(FieldStatusPermission::getId)
                        .collect(Collectors.toSet());
            }
            
            Map<Long, PermissionAssignment> workerAssignments = permissionAssignmentService.getAssignments(
                "WorkerPermission", workerPermissionIds, tenant);
        Map<Long, PermissionAssignment> workerProjectAssignments = projectId != null
                ? projectPermissionAssignmentService.getProjectAssignments("WorkerPermission", workerPermissionIds, projectId, tenant)
                : Collections.emptyMap();
        for (ItemTypeConfiguration config : configurations) {
            List<WorkerPermission> permissions = workerPermissionsByConfigId.getOrDefault(config.getId(), Collections.emptyList());
            for (WorkerPermission perm : permissions) {
                Map<String, Object> worker = new HashMap<>();
                worker.put("id", perm.getId());
                worker.put("name", "Workers");
                worker.put("permissionType", "WorkerPermission"); // Aggiunto per il frontend
                Map<String, Object> itemTypeMap = new HashMap<>();
                itemTypeMap.put("id", config.getItemType().getId());
                itemTypeMap.put("name", config.getItemType().getName());
                worker.put("itemType", itemTypeMap);
                
                PermissionAssignment assignment = workerAssignments.get(perm.getId());
                PermissionAssignment projectAssignment = workerProjectAssignments.get(perm.getId());
                
                Set<Role> assignedRolesSet = assignment != null && assignment.getRoles() != null
                        ? assignment.getRoles()
                        : Collections.emptySet();
                worker.put("assignedRolesCount", assignedRolesSet.size());
                
                Grant grant = assignment != null ? assignment.getGrant() : null;
                if (grant != null) {
                    worker.put("grantId", grant.getId());
                    worker.put("grantName", "Grant diretto");
                }
                
                boolean hasAssignments = !assignedRolesSet.isEmpty() || grant != null;
                
                if (projectAssignment != null) {
                    Grant projectGrant = projectAssignment.getGrant();
                    if (projectGrant != null) {
                        worker.put("projectGrantId", projectGrant.getId());
                        worker.put("projectGrantName", "Grant di progetto");
                        hasAssignments = true;
                    }
                    
                    Set<Role> projectRoles = projectAssignment.getRoles() != null
                            ? projectAssignment.getRoles()
                            : Collections.emptySet();
                    if (!projectRoles.isEmpty()) {
                        worker.put("projectAssignedRoles", mapRoles(projectRoles));
                        worker.put("hasProjectRoles", true);
                        hasAssignments = true;
                    } else {
                        worker.put("hasProjectRoles", false);
                    }
                }
                
                worker.put("hasAssignments", hasAssignments);
                worker.put("assignedRoles", mapRoles(assignedRolesSet));
                
                workers.add(worker);
            }
        }
        result.put("Workers", workers); // Chiave aggiornata: usa nome completo invece di "WORKERS"
        
        // StatusOwner permissions
        List<Map<String, Object>> statusOwners = new ArrayList<>();
        Map<Long, PermissionAssignment> statusAssignments = permissionAssignmentService.getAssignments(
                "StatusOwnerPermission", statusPermissionIds, tenant);
        Map<Long, PermissionAssignment> statusProjectAssignments = projectId != null
                ? projectPermissionAssignmentService.getProjectAssignments("StatusOwnerPermission", statusPermissionIds, projectId, tenant)
                : Collections.emptyMap();
        for (ItemTypeConfiguration config : configurations) {
            List<StatusOwnerPermission> permissions = statusPermissionsByConfigId.getOrDefault(config.getId(), Collections.emptyList());
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
                
                PermissionAssignment assignment = statusAssignments.get(perm.getId());
                PermissionAssignment projectAssignment = statusProjectAssignments.get(perm.getId());
                
                Set<Role> assignedRolesSet = assignment != null && assignment.getRoles() != null
                        ? assignment.getRoles()
                        : Collections.emptySet();
                statusOwner.put("assignedRolesCount", assignedRolesSet.size());
                
                Grant grant = assignment != null ? assignment.getGrant() : null;
                if (grant != null) {
                    statusOwner.put("grantId", grant.getId());
                    statusOwner.put("grantName", "Grant diretto");
                }
                
                boolean hasAssignments = !assignedRolesSet.isEmpty() || grant != null;
                
                if (projectAssignment != null) {
                    Grant projectGrant = projectAssignment.getGrant();
                    if (projectGrant != null) {
                        statusOwner.put("projectGrantId", projectGrant.getId());
                        statusOwner.put("projectGrantName", "Grant di progetto");
                        hasAssignments = true;
                    }
                    
                    Set<Role> projectRoles = projectAssignment.getRoles() != null
                            ? projectAssignment.getRoles()
                            : Collections.emptySet();
                    if (!projectRoles.isEmpty()) {
                        statusOwner.put("projectAssignedRoles", mapRoles(projectRoles));
                        statusOwner.put("hasProjectRoles", true);
                        hasAssignments = true;
                    } else {
                        statusOwner.put("hasProjectRoles", false);
                    }
                }
                
                statusOwner.put("hasAssignments", hasAssignments);
                statusOwner.put("assignedRoles", mapRoles(assignedRolesSet));
                
                statusOwners.add(statusOwner);
            }
        }
        result.put("Status Owners", statusOwners); // Chiave aggiornata: usa nome completo invece di "STATUS_OWNERS"
        
        // FieldOwner permissions
        List<Map<String, Object>> fieldOwners = new ArrayList<>();
        Map<Long, PermissionAssignment> fieldOwnerAssignments = permissionAssignmentService.getAssignments(
                "FieldOwnerPermission", fieldOwnerPermissionIds, tenant);
        Map<Long, PermissionAssignment> fieldOwnerProjectAssignments = projectId != null
                ? projectPermissionAssignmentService.getProjectAssignments("FieldOwnerPermission", fieldOwnerPermissionIds, projectId, tenant)
                : Collections.emptyMap();
        for (ItemTypeConfiguration config : configurations) {
            List<FieldOwnerPermission> permissions = fieldOwnerPermissionsByConfigId.getOrDefault(config.getId(), Collections.emptyList());
            for (FieldOwnerPermission perm : permissions) {
                Map<String, Object> fieldOwner = new HashMap<>();
                fieldOwner.put("id", perm.getId());
                fieldOwner.put("name", "Field Owners");
                fieldOwner.put("permissionType", "FieldOwnerPermission"); // Aggiunto per il frontend
                Map<String, Object> fieldConfigMap = new HashMap<>();
                fieldConfigMap.put("id", perm.getField().getId());
                fieldConfigMap.put("name", perm.getField().getName());
                fieldConfigMap.put("fieldType", null); // Field non ha fieldType, potrebbe essere nella FieldConfiguration
                fieldOwner.put("fieldConfiguration", fieldConfigMap);
                
                Map<String, Object> itemTypeMap3 = new HashMap<>();
                itemTypeMap3.put("id", config.getItemType().getId());
                itemTypeMap3.put("name", config.getItemType().getName());
                fieldOwner.put("itemType", itemTypeMap3);
                
                PermissionAssignment assignment = fieldOwnerAssignments.get(perm.getId());
                PermissionAssignment projectAssignment = fieldOwnerProjectAssignments.get(perm.getId());
                
                Set<Role> assignedRolesSet = assignment != null && assignment.getRoles() != null
                        ? assignment.getRoles()
                        : Collections.emptySet();
                fieldOwner.put("assignedRolesCount", assignedRolesSet.size());
                
                Grant grant = assignment != null ? assignment.getGrant() : null;
                if (grant != null) {
                    fieldOwner.put("grantId", grant.getId());
                    fieldOwner.put("grantName", "Grant diretto");
                }
                
                boolean hasAssignments = !assignedRolesSet.isEmpty() || grant != null;
                
                if (projectAssignment != null) {
                    Grant projectGrant = projectAssignment.getGrant();
                    if (projectGrant != null) {
                        fieldOwner.put("projectGrantId", projectGrant.getId());
                        fieldOwner.put("projectGrantName", "Grant di progetto");
                        hasAssignments = true;
                    }
                    
                    Set<Role> projectRoles = projectAssignment.getRoles() != null
                            ? projectAssignment.getRoles()
                            : Collections.emptySet();
                    if (!projectRoles.isEmpty()) {
                        fieldOwner.put("projectAssignedRoles", mapRoles(projectRoles));
                        fieldOwner.put("hasProjectRoles", true);
                        hasAssignments = true;
                    } else {
                        fieldOwner.put("hasProjectRoles", false);
                    }
                }
                
                fieldOwner.put("hasAssignments", hasAssignments);
                fieldOwner.put("assignedRoles", mapRoles(assignedRolesSet));
                
                fieldOwners.add(fieldOwner);
            }
        }
        result.put("Field Owners", fieldOwners); // Chiave aggiornata: usa nome completo invece di "FIELD_OWNERS"
        
        // Creator permissions
        List<Map<String, Object>> creators = new ArrayList<>();
        Map<Long, PermissionAssignment> creatorAssignments = permissionAssignmentService.getAssignments(
                "CreatorPermission", creatorPermissionIds, tenant);
        Map<Long, PermissionAssignment> creatorProjectAssignments = projectId != null
                ? projectPermissionAssignmentService.getProjectAssignments("CreatorPermission", creatorPermissionIds, projectId, tenant)
                : Collections.emptyMap();
        for (ItemTypeConfiguration config : configurations) {
            List<CreatorPermission> permissions = creatorPermissionsByConfigId.getOrDefault(config.getId(), Collections.emptyList());
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
                
                PermissionAssignment assignment = creatorAssignments.get(perm.getId());
                PermissionAssignment projectAssignment = creatorProjectAssignments.get(perm.getId());
                
                Set<Role> assignedRolesSet = assignment != null && assignment.getRoles() != null
                        ? assignment.getRoles()
                        : Collections.emptySet();
                creator.put("assignedRolesCount", assignedRolesSet.size());
                
                Grant grant = assignment != null ? assignment.getGrant() : null;
                if (grant != null) {
                    creator.put("grantId", grant.getId());
                    creator.put("grantName", "Grant diretto");
                }
                
                boolean hasAssignments = !assignedRolesSet.isEmpty() || grant != null;
                
                if (projectAssignment != null) {
                    Grant projectGrant = projectAssignment.getGrant();
                    if (projectGrant != null) {
                        creator.put("projectGrantId", projectGrant.getId());
                        creator.put("projectGrantName", "Grant di progetto");
                        hasAssignments = true;
                    }
                    
                    Set<Role> projectRoles = projectAssignment.getRoles() != null
                            ? projectAssignment.getRoles()
                            : Collections.emptySet();
                    if (!projectRoles.isEmpty()) {
                        creator.put("projectAssignedRoles", mapRoles(projectRoles));
                        creator.put("hasProjectRoles", true);
                        hasAssignments = true;
                    } else {
                        creator.put("hasProjectRoles", false);
                    }
                }
                
                creator.put("hasAssignments", hasAssignments);
                creator.put("assignedRoles", mapRoles(assignedRolesSet));
                
                creators.add(creator);
            }
        }
        result.put("Creators", creators); // Chiave aggiornata: usa nome completo invece di "CREATORS"
        
        // Executor permissions
        List<Map<String, Object>> executors = new ArrayList<>();
        Map<Long, PermissionAssignment> executorAssignments = permissionAssignmentService.getAssignments(
                "ExecutorPermission", executorPermissionIds, tenant);
        Map<Long, PermissionAssignment> executorProjectAssignments = projectId != null
                ? projectPermissionAssignmentService.getProjectAssignments("ExecutorPermission", executorPermissionIds, projectId, tenant)
                : Collections.emptyMap();
        for (ItemTypeConfiguration config : configurations) {
            List<ExecutorPermission> permissions = executorPermissionsByConfigId.getOrDefault(config.getId(), Collections.emptyList());
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
                
                PermissionAssignment assignment = executorAssignments.get(perm.getId());
                PermissionAssignment projectAssignment = executorProjectAssignments.get(perm.getId());
                
                Set<Role> assignedRolesSet = assignment != null && assignment.getRoles() != null
                        ? assignment.getRoles()
                        : Collections.emptySet();
                executor.put("assignedRolesCount", assignedRolesSet.size());
                
                Grant grant = assignment != null ? assignment.getGrant() : null;
                if (grant != null) {
                    executor.put("grantId", grant.getId());
                    executor.put("grantName", "Grant diretto");
                }
                
                boolean hasAssignments = !assignedRolesSet.isEmpty() || grant != null;
                
                if (projectAssignment != null) {
                    Grant projectGrant = projectAssignment.getGrant();
                    if (projectGrant != null) {
                        executor.put("projectGrantId", projectGrant.getId());
                        executor.put("projectGrantName", "Grant di progetto");
                        hasAssignments = true;
                    }
                    
                    Set<Role> projectRoles = projectAssignment.getRoles() != null
                            ? projectAssignment.getRoles()
                            : Collections.emptySet();
                    if (!projectRoles.isEmpty()) {
                        executor.put("projectAssignedRoles", mapRoles(projectRoles));
                        executor.put("hasProjectRoles", true);
                        hasAssignments = true;
                    } else {
                        executor.put("hasProjectRoles", false);
                    }
                }
                
                executor.put("hasAssignments", hasAssignments);
                executor.put("assignedRoles", mapRoles(assignedRolesSet));
                
                executors.add(executor);
            }
        }
        result.put("Executors", executors); // Chiave aggiornata: usa nome completo invece di "EXECUTORS"
        
        // Editor permissions
        List<Map<String, Object>> editors = new ArrayList<>();
        Map<Long, PermissionAssignment> editorAssignments = permissionAssignmentService.getAssignments(
                "FieldStatusPermission", editorPermissionIds, tenant);
        Map<Long, PermissionAssignment> editorProjectAssignments = projectId != null
                ? projectPermissionAssignmentService.getProjectAssignments("FieldStatusPermission", editorPermissionIds, projectId, tenant)
                : Collections.emptyMap();
        for (ItemTypeConfiguration config : configurations) {
            List<FieldStatusPermission> permissions = editorsByConfigId.getOrDefault(config.getId(), Collections.emptyList());
            for (FieldStatusPermission perm : permissions) {
                Map<String, Object> editor = new HashMap<>();
                editor.put("id", perm.getId());
                editor.put("name", "Editors");
                editor.put("permissionType", "FieldStatusPermission"); // Aggiunto per il frontend
                Map<String, Object> fieldConfigMap2 = new HashMap<>();
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
                
                PermissionAssignment assignment = editorAssignments.get(perm.getId());
                PermissionAssignment projectAssignment = editorProjectAssignments.get(perm.getId());
                
                Set<Role> assignedRolesSet = assignment != null && assignment.getRoles() != null
                        ? assignment.getRoles()
                        : Collections.emptySet();
                editor.put("assignedRolesCount", assignedRolesSet.size());
                
                Grant grant = assignment != null ? assignment.getGrant() : null;
                if (grant != null) {
                    editor.put("grantId", grant.getId());
                    editor.put("grantName", "Grant diretto");
                }
                
                boolean hasAssignments = !assignedRolesSet.isEmpty() || grant != null;
                
                if (projectAssignment != null) {
                    Grant projectGrant = projectAssignment.getGrant();
                    if (projectGrant != null) {
                        editor.put("projectGrantId", projectGrant.getId());
                        editor.put("projectGrantName", "Grant di progetto");
                        hasAssignments = true;
                    }
                    
                    Set<Role> projectRoles = projectAssignment.getRoles() != null
                            ? projectAssignment.getRoles()
                            : Collections.emptySet();
                    if (!projectRoles.isEmpty()) {
                        editor.put("projectAssignedRoles", mapRoles(projectRoles));
                        editor.put("hasProjectRoles", true);
                        hasAssignments = true;
                    } else {
                        editor.put("hasProjectRoles", false);
                    }
                }
                
                editor.put("hasAssignments", hasAssignments);
                editor.put("assignedRoles", mapRoles(assignedRolesSet));
                
                editors.add(editor);
            }
        }
        result.put("Editors", editors); // Chiave aggiornata: usa nome completo invece di "EDITORS"
        
        // Viewer permissions
        List<Map<String, Object>> viewers = new ArrayList<>();
        Map<Long, PermissionAssignment> viewerAssignments = permissionAssignmentService.getAssignments(
                "FieldStatusPermission", viewerPermissionIds, tenant);
        Map<Long, PermissionAssignment> viewerProjectAssignments = projectId != null
                ? projectPermissionAssignmentService.getProjectAssignments("FieldStatusPermission", viewerPermissionIds, projectId, tenant)
                : Collections.emptyMap();
        for (ItemTypeConfiguration config : configurations) {
            List<FieldStatusPermission> permissions = viewersByConfigId.getOrDefault(config.getId(), Collections.emptyList());
            for (FieldStatusPermission perm : permissions) {
                Map<String, Object> viewer = new HashMap<>();
                viewer.put("id", perm.getId());
                viewer.put("name", "Viewers");
                viewer.put("permissionType", "FieldStatusPermission"); // Aggiunto per il frontend
                Map<String, Object> fieldConfigMap3 = new HashMap<>();
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
                
                PermissionAssignment assignment = viewerAssignments.get(perm.getId());
                PermissionAssignment projectAssignment = viewerProjectAssignments.get(perm.getId());
                
                Set<Role> assignedRolesSet = assignment != null && assignment.getRoles() != null
                        ? assignment.getRoles()
                        : Collections.emptySet();
                viewer.put("assignedRolesCount", assignedRolesSet.size());
                
                Grant grant = assignment != null ? assignment.getGrant() : null;
                if (grant != null) {
                    viewer.put("grantId", grant.getId());
                    viewer.put("grantName", "Grant diretto");
                }
                
                boolean hasAssignments = !assignedRolesSet.isEmpty() || grant != null;
                
                if (projectAssignment != null) {
                    Grant projectGrant = projectAssignment.getGrant();
                    if (projectGrant != null) {
                        viewer.put("projectGrantId", projectGrant.getId());
                        viewer.put("projectGrantName", "Grant di progetto");
                        hasAssignments = true;
                    }
                    
                    Set<Role> projectRoles = projectAssignment.getRoles() != null
                            ? projectAssignment.getRoles()
                            : Collections.emptySet();
                    if (!projectRoles.isEmpty()) {
                        viewer.put("projectAssignedRoles", mapRoles(projectRoles));
                        viewer.put("hasProjectRoles", true);
                        hasAssignments = true;
                    } else {
                        viewer.put("hasProjectRoles", false);
                    }
                }
                
                viewer.put("hasAssignments", hasAssignments);
                viewer.put("assignedRoles", mapRoles(assignedRolesSet));
                
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
    
    private List<Map<String, Object>> mapRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> roleDtos = new ArrayList<>();
        for (Role role : roles) {
            Map<String, Object> roleMap = new HashMap<>();
            roleMap.put("id", role.getId());
            roleMap.put("name", role.getName());
            roleMap.put("description", role.getDescription());
            roleDtos.add(roleMap);
        }
        return roleDtos;
    }
}
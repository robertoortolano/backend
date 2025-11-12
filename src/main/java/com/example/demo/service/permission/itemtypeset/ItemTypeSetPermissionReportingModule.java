package com.example.demo.service.permission.itemtypeset;

import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.*;
import com.example.demo.service.permission.scope.PermissionScope;
import com.example.demo.service.permission.scope.PermissionScopeRegistry;
import com.example.demo.service.permission.scope.PermissionScopeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ItemTypeSetPermissionReportingModule {

    private final ItemTypeSetRepository itemTypeSetRepository;
    private final WorkerPermissionRepository workerPermissionRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final CreatorPermissionRepository creatorPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final PermissionScopeRegistry permissionScopeRegistry;

    public Map<String, List<Map<String, Object>>> getPermissionsByItemTypeSet(Long itemTypeSetId,
                                                                             Tenant tenant,
                                                                             Long projectId) {
        try {
            ItemTypeSet itemTypeSet = itemTypeSetRepository.findByIdWithAllRelations(itemTypeSetId, tenant)
                    .orElseThrow(() -> new ApiException("ItemTypeSet not found"));

            Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();

            // Assicurati che le configurazioni siano caricate correttamente
            // Per ItemTypeSet di progetto, potrebbe essere necessario ricaricare le configurazioni
            List<ItemTypeConfiguration> configurations = new ArrayList<>(itemTypeSet.getItemTypeConfigurations());
            
            // Se non ci sono configurazioni caricate, ricarica l'ItemTypeSet
            if (configurations.isEmpty() && itemTypeSet.getId() != null) {
                ItemTypeSet reloadedItemTypeSet = itemTypeSetRepository.findByIdWithItemTypeConfigurationsAndTenant(
                        itemTypeSet.getId(), 
                        tenant
                ).orElse(itemTypeSet);
                configurations = new ArrayList<>(reloadedItemTypeSet.getItemTypeConfigurations());
                // Aggiorna anche l'ItemTypeSet originale con le configurazioni ricaricate
                itemTypeSet.setItemTypeConfigurations(new HashSet<>(configurations));
            }
            
            Set<Long> configurationIds = configurations.stream()
                    .map(ItemTypeConfiguration::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            
            // Log per debug: verifica che le configurazioni siano corrette
            if (log.isDebugEnabled()) {
                log.debug("ItemTypeSet {} (scope: {}) has {} configurations: {}", 
                        itemTypeSet.getId(), 
                        itemTypeSet.getScope(), 
                        configurationIds.size(),
                        configurationIds);
            }
            
            // Verifica che le configurazioni siano effettivamente nell'ItemTypeSet
            // Questo è importante per gli ItemTypeSet di progetto
            // Le configurazioni sono già state caricate con LEFT JOIN FETCH, quindi dovrebbero essere corrette
            // Non serve una verifica aggiuntiva qui perché le permission vengono già filtrate per configurationIds

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

            PermissionScopeRequest tenantScopeRequest = PermissionScopeRequest.forTenant(tenant);
            Map<Long, PermissionAssignment> workerAssignments = permissionScopeRegistry
                    .getHandler(PermissionScope.TENANT)
                    .getAssignments("WorkerPermission", workerPermissionIds, tenantScopeRequest);
            Map<Long, PermissionAssignment> statusAssignments = permissionScopeRegistry
                    .getHandler(PermissionScope.TENANT)
                    .getAssignments("StatusOwnerPermission", statusPermissionIds, tenantScopeRequest);
            Map<Long, PermissionAssignment> fieldOwnerAssignments = permissionScopeRegistry
                    .getHandler(PermissionScope.TENANT)
                    .getAssignments("FieldOwnerPermission", fieldOwnerPermissionIds, tenantScopeRequest);
            Map<Long, PermissionAssignment> creatorAssignments = permissionScopeRegistry
                    .getHandler(PermissionScope.TENANT)
                    .getAssignments("CreatorPermission", creatorPermissionIds, tenantScopeRequest);
            Map<Long, PermissionAssignment> executorAssignments = permissionScopeRegistry
                    .getHandler(PermissionScope.TENANT)
                    .getAssignments("ExecutorPermission", executorPermissionIds, tenantScopeRequest);
            Map<Long, PermissionAssignment> editorAssignments = permissionScopeRegistry
                    .getHandler(PermissionScope.TENANT)
                    .getAssignments("FieldStatusPermission", editorPermissionIds, tenantScopeRequest);
            Map<Long, PermissionAssignment> viewerAssignments = permissionScopeRegistry
                    .getHandler(PermissionScope.TENANT)
                    .getAssignments("FieldStatusPermission", viewerPermissionIds, tenantScopeRequest);

            Map<Long, PermissionAssignment> workerProjectAssignments = Collections.emptyMap();
            Map<Long, PermissionAssignment> statusProjectAssignments = Collections.emptyMap();
            Map<Long, PermissionAssignment> fieldOwnerProjectAssignments = Collections.emptyMap();
            Map<Long, PermissionAssignment> creatorProjectAssignments = Collections.emptyMap();
            Map<Long, PermissionAssignment> executorProjectAssignments = Collections.emptyMap();
            Map<Long, PermissionAssignment> editorProjectAssignments = Collections.emptyMap();
            Map<Long, PermissionAssignment> viewerProjectAssignments = Collections.emptyMap();

            if (projectId != null) {
                PermissionScopeRequest projectScopeRequest = PermissionScopeRequest.forProject(tenant, projectId);
                workerProjectAssignments = permissionScopeRegistry.getHandler(PermissionScope.PROJECT)
                        .getAssignments("WorkerPermission", workerPermissionIds, projectScopeRequest);
                statusProjectAssignments = permissionScopeRegistry.getHandler(PermissionScope.PROJECT)
                        .getAssignments("StatusOwnerPermission", statusPermissionIds, projectScopeRequest);
                fieldOwnerProjectAssignments = permissionScopeRegistry.getHandler(PermissionScope.PROJECT)
                        .getAssignments("FieldOwnerPermission", fieldOwnerPermissionIds, projectScopeRequest);
                creatorProjectAssignments = permissionScopeRegistry.getHandler(PermissionScope.PROJECT)
                        .getAssignments("CreatorPermission", creatorPermissionIds, projectScopeRequest);
                executorProjectAssignments = permissionScopeRegistry.getHandler(PermissionScope.PROJECT)
                        .getAssignments("ExecutorPermission", executorPermissionIds, projectScopeRequest);
                editorProjectAssignments = permissionScopeRegistry.getHandler(PermissionScope.PROJECT)
                        .getAssignments("FieldStatusPermission", editorPermissionIds, projectScopeRequest);
                viewerProjectAssignments = permissionScopeRegistry.getHandler(PermissionScope.PROJECT)
                        .getAssignments("FieldStatusPermission", viewerPermissionIds, projectScopeRequest);
            }

            List<Map<String, Object>> workers = buildWorkerPermissions(configurations,
                    workerPermissionsByConfigId,
                    workerAssignments,
                    workerProjectAssignments);
            result.put("Workers", workers);

            List<Map<String, Object>> statusOwners = buildStatusOwnerPermissions(configurations,
                    statusPermissionsByConfigId,
                    statusAssignments,
                    statusProjectAssignments);
            result.put("Status Owners", statusOwners);

            List<Map<String, Object>> fieldOwners = buildFieldOwnerPermissions(configurations,
                    fieldOwnerPermissionsByConfigId,
                    fieldOwnerAssignments,
                    fieldOwnerProjectAssignments);
            result.put("Field Owners", fieldOwners);

            List<Map<String, Object>> creators = buildCreatorPermissions(configurations,
                    creatorPermissionsByConfigId,
                    creatorAssignments,
                    creatorProjectAssignments);
            result.put("Creators", creators);

            List<Map<String, Object>> executors = buildExecutorPermissions(configurations,
                    executorPermissionsByConfigId,
                    executorAssignments,
                    executorProjectAssignments);
            result.put("Executors", executors);

            List<Map<String, Object>> editors = buildEditorPermissions(configurations,
                    editorsByConfigId,
                    editorAssignments,
                    editorProjectAssignments,
                    "Editors");
            result.put("Editors", editors);

            List<Map<String, Object>> viewers = buildEditorPermissions(configurations,
                    viewersByConfigId,
                    viewerAssignments,
                    viewerProjectAssignments,
                    "Viewers");
            result.put("Viewers", viewers);

            return result;
        } catch (Exception e) {
            log.error("Error retrieving permissions", e);
            throw new ApiException("Error retrieving permissions: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> buildWorkerPermissions(
            List<ItemTypeConfiguration> configurations,
            Map<Long, List<WorkerPermission>> permissionsByConfigId,
            Map<Long, PermissionAssignment> tenantAssignments,
            Map<Long, PermissionAssignment> projectAssignments
    ) {
        List<Map<String, Object>> workers = new ArrayList<>();
        for (ItemTypeConfiguration config : configurations) {
            List<WorkerPermission> permissions = permissionsByConfigId
                    .getOrDefault(config.getId(), Collections.emptyList());
            for (WorkerPermission perm : permissions) {
                Map<String, Object> worker = new HashMap<>();
                worker.put("id", perm.getId());
                worker.put("name", "Workers");
                worker.put("permissionType", "WorkerPermission");

                Map<String, Object> itemTypeMap = new HashMap<>();
                itemTypeMap.put("id", config.getItemType().getId());
                itemTypeMap.put("name", config.getItemType().getName());
                worker.put("itemType", itemTypeMap);

                populateAssignments(worker, tenantAssignments.get(perm.getId()), projectAssignments.get(perm.getId()));

                workers.add(worker);
            }
        }
        return workers;
    }

    private List<Map<String, Object>> buildStatusOwnerPermissions(
            List<ItemTypeConfiguration> configurations,
            Map<Long, List<StatusOwnerPermission>> permissionsByConfigId,
            Map<Long, PermissionAssignment> tenantAssignments,
            Map<Long, PermissionAssignment> projectAssignments
    ) {
        List<Map<String, Object>> statusOwners = new ArrayList<>();
        for (ItemTypeConfiguration config : configurations) {
            List<StatusOwnerPermission> permissions = permissionsByConfigId
                    .getOrDefault(config.getId(), Collections.emptyList());
            for (StatusOwnerPermission perm : permissions) {
                Map<String, Object> statusOwner = new HashMap<>();
                statusOwner.put("id", perm.getId());
                statusOwner.put("name", "Status Owners");
                statusOwner.put("permissionType", "StatusOwnerPermission");

                Map<String, Object> workflowStatusMap = new HashMap<>();
                workflowStatusMap.put("id", perm.getWorkflowStatus().getId());
                workflowStatusMap.put("name", perm.getWorkflowStatus().getStatus().getName());
                statusOwner.put("workflowStatus", workflowStatusMap);

                Map<String, Object> workflowMap = new HashMap<>();
                workflowMap.put("id", config.getWorkflow().getId());
                workflowMap.put("name", config.getWorkflow().getName());
                statusOwner.put("workflow", workflowMap);

                Map<String, Object> itemTypeMap = new HashMap<>();
                itemTypeMap.put("id", config.getItemType().getId());
                itemTypeMap.put("name", config.getItemType().getName());
                statusOwner.put("itemType", itemTypeMap);

                populateAssignments(statusOwner, tenantAssignments.get(perm.getId()),
                        projectAssignments.get(perm.getId()));

                statusOwners.add(statusOwner);
            }
        }
        return statusOwners;
    }

    private List<Map<String, Object>> buildFieldOwnerPermissions(
            List<ItemTypeConfiguration> configurations,
            Map<Long, List<FieldOwnerPermission>> permissionsByConfigId,
            Map<Long, PermissionAssignment> tenantAssignments,
            Map<Long, PermissionAssignment> projectAssignments
    ) {
        List<Map<String, Object>> fieldOwners = new ArrayList<>();
        for (ItemTypeConfiguration config : configurations) {
            List<FieldOwnerPermission> permissions = permissionsByConfigId
                    .getOrDefault(config.getId(), Collections.emptyList());
            for (FieldOwnerPermission perm : permissions) {
                Map<String, Object> fieldOwner = new HashMap<>();
                fieldOwner.put("id", perm.getId());
                fieldOwner.put("name", "Field Owners");
                fieldOwner.put("permissionType", "FieldOwnerPermission");

                Map<String, Object> fieldConfigMap = new HashMap<>();
                fieldConfigMap.put("id", perm.getField().getId());
                fieldConfigMap.put("name", perm.getField().getName());
                fieldConfigMap.put("fieldType", null);
                fieldOwner.put("fieldConfiguration", fieldConfigMap);

                Map<String, Object> itemTypeMap = new HashMap<>();
                itemTypeMap.put("id", config.getItemType().getId());
                itemTypeMap.put("name", config.getItemType().getName());
                fieldOwner.put("itemType", itemTypeMap);

                populateAssignments(fieldOwner, tenantAssignments.get(perm.getId()),
                        projectAssignments.get(perm.getId()));

                fieldOwners.add(fieldOwner);
            }
        }
        return fieldOwners;
    }

    private List<Map<String, Object>> buildCreatorPermissions(
            List<ItemTypeConfiguration> configurations,
            Map<Long, List<CreatorPermission>> permissionsByConfigId,
            Map<Long, PermissionAssignment> tenantAssignments,
            Map<Long, PermissionAssignment> projectAssignments
    ) {
        List<Map<String, Object>> creators = new ArrayList<>();
        for (ItemTypeConfiguration config : configurations) {
            List<CreatorPermission> permissions = permissionsByConfigId
                    .getOrDefault(config.getId(), Collections.emptyList());
            for (CreatorPermission perm : permissions) {
                Map<String, Object> creator = new HashMap<>();
                creator.put("id", perm.getId());
                creator.put("name", "Creators");
                creator.put("permissionType", "CreatorPermission");

                Map<String, Object> workflowMap = new HashMap<>();
                workflowMap.put("id", config.getWorkflow().getId());
                workflowMap.put("name", config.getWorkflow().getName());
                creator.put("workflow", workflowMap);

                Map<String, Object> itemTypeMap = new HashMap<>();
                itemTypeMap.put("id", config.getItemType().getId());
                itemTypeMap.put("name", config.getItemType().getName());
                creator.put("itemType", itemTypeMap);

                populateAssignments(creator, tenantAssignments.get(perm.getId()),
                        projectAssignments.get(perm.getId()));

                creators.add(creator);
            }
        }
        return creators;
    }

    private List<Map<String, Object>> buildExecutorPermissions(
            List<ItemTypeConfiguration> configurations,
            Map<Long, List<ExecutorPermission>> permissionsByConfigId,
            Map<Long, PermissionAssignment> tenantAssignments,
            Map<Long, PermissionAssignment> projectAssignments
    ) {
        List<Map<String, Object>> executors = new ArrayList<>();
        for (ItemTypeConfiguration config : configurations) {
            List<ExecutorPermission> permissions = permissionsByConfigId
                    .getOrDefault(config.getId(), Collections.emptyList());
            for (ExecutorPermission perm : permissions) {
                Map<String, Object> executor = new HashMap<>();
                executor.put("id", perm.getId());
                executor.put("name", "Executors");
                executor.put("permissionType", "ExecutorPermission");

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

                Map<String, Object> workflowMap = new HashMap<>();
                workflowMap.put("id", config.getWorkflow().getId());
                workflowMap.put("name", config.getWorkflow().getName());
                executor.put("workflow", workflowMap);

                Map<String, Object> itemTypeMap = new HashMap<>();
                itemTypeMap.put("id", config.getItemType().getId());
                itemTypeMap.put("name", config.getItemType().getName());
                executor.put("itemType", itemTypeMap);

                populateAssignments(executor, tenantAssignments.get(perm.getId()),
                        projectAssignments.get(perm.getId()));

                executors.add(executor);
            }
        }
        return executors;
    }

    private List<Map<String, Object>> buildEditorPermissions(
            List<ItemTypeConfiguration> configurations,
            Map<Long, List<FieldStatusPermission>> permissionsByConfigId,
            Map<Long, PermissionAssignment> tenantAssignments,
            Map<Long, PermissionAssignment> projectAssignments,
            String label
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ItemTypeConfiguration config : configurations) {
            List<FieldStatusPermission> permissions = permissionsByConfigId
                    .getOrDefault(config.getId(), Collections.emptyList());
            for (FieldStatusPermission perm : permissions) {
                Map<String, Object> permissionMap = new HashMap<>();
                permissionMap.put("id", perm.getId());
                permissionMap.put("name", label);
                permissionMap.put("permissionType", "FieldStatusPermission");

                Map<String, Object> fieldConfigMap = new HashMap<>();
                fieldConfigMap.put("id", perm.getField().getId());
                fieldConfigMap.put("name", perm.getField().getName());
                permissionMap.put("fieldConfiguration", fieldConfigMap);

                Map<String, Object> workflowStatusMap = new HashMap<>();
                workflowStatusMap.put("id", perm.getWorkflowStatus().getId());
                workflowStatusMap.put("name", perm.getWorkflowStatus().getStatus().getName());
                permissionMap.put("workflowStatus", workflowStatusMap);

                Map<String, Object> itemTypeMap = new HashMap<>();
                itemTypeMap.put("id", config.getItemType().getId());
                itemTypeMap.put("name", config.getItemType().getName());
                permissionMap.put("itemType", itemTypeMap);

                populateAssignments(permissionMap, tenantAssignments.get(perm.getId()),
                        projectAssignments.get(perm.getId()));

                result.add(permissionMap);
            }
        }
        return result;
    }

    private void populateAssignments(
            Map<String, Object> target,
            PermissionAssignment tenantAssignment,
            PermissionAssignment projectAssignment
    ) {
        Set<Role> assignedRolesSet = tenantAssignment != null && tenantAssignment.getRoles() != null
                ? tenantAssignment.getRoles()
                : Collections.emptySet();
        target.put("assignedRolesCount", assignedRolesSet.size());

        Grant grant = tenantAssignment != null ? tenantAssignment.getGrant() : null;
        if (grant != null) {
            target.put("grantId", grant.getId());
            target.put("grantName", "Grant diretto");
        }

        boolean hasAssignments = !assignedRolesSet.isEmpty() || grant != null;

        if (projectAssignment != null) {
            Grant projectGrant = projectAssignment.getGrant();
            if (projectGrant != null) {
                target.put("projectGrantId", projectGrant.getId());
                target.put("projectGrantName", "Grant di progetto");
                // Includi i dettagli completi del grant per evitare chiamate separate dal frontend
                Map<String, Object> projectGrantDetails = new HashMap<>();
                projectGrantDetails.put("id", projectGrant.getId());
                // Converti Set<User> e Set<Group> in liste di ID per la serializzazione JSON
                projectGrantDetails.put("users", projectGrant.getUsers() != null 
                    ? projectGrant.getUsers().stream()
                        .map(user -> {
                            Map<String, Object> userMap = new HashMap<>();
                            userMap.put("id", user.getId());
                            userMap.put("username", user.getUsername());
                            userMap.put("fullName", user.getFullName());
                            return userMap;
                        })
                        .collect(Collectors.toList())
                    : Collections.emptyList());
                projectGrantDetails.put("groups", projectGrant.getGroups() != null
                    ? projectGrant.getGroups().stream()
                        .map(group -> {
                            Map<String, Object> groupMap = new HashMap<>();
                            groupMap.put("id", group.getId());
                            groupMap.put("name", group.getName());
                            return groupMap;
                        })
                        .collect(Collectors.toList())
                    : Collections.emptyList());
                projectGrantDetails.put("negatedUsers", projectGrant.getNegatedUsers() != null
                    ? projectGrant.getNegatedUsers().stream()
                        .map(user -> {
                            Map<String, Object> userMap = new HashMap<>();
                            userMap.put("id", user.getId());
                            userMap.put("username", user.getUsername());
                            userMap.put("fullName", user.getFullName());
                            return userMap;
                        })
                        .collect(Collectors.toList())
                    : Collections.emptyList());
                projectGrantDetails.put("negatedGroups", projectGrant.getNegatedGroups() != null
                    ? projectGrant.getNegatedGroups().stream()
                        .map(group -> {
                            Map<String, Object> groupMap = new HashMap<>();
                            groupMap.put("id", group.getId());
                            groupMap.put("name", group.getName());
                            return groupMap;
                        })
                        .collect(Collectors.toList())
                    : Collections.emptyList());
                target.put("projectGrant", projectGrantDetails);
                hasAssignments = true;
            }

            Set<Role> projectRoles = projectAssignment.getRoles() != null
                    ? projectAssignment.getRoles()
                    : Collections.emptySet();
            if (!projectRoles.isEmpty()) {
                target.put("projectAssignedRoles", mapRoles(projectRoles));
                target.put("hasProjectRoles", true);
                hasAssignments = true;
            } else {
                target.put("hasProjectRoles", false);
            }
        }

        target.put("hasAssignments", hasAssignments);
        target.put("assignedRoles", mapRoles(assignedRolesSet));

        if (!target.containsKey("hasProjectRoles")) {
            target.put("hasProjectRoles", false);
        }
    }

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



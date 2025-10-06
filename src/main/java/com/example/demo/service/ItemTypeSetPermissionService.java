package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class ItemTypeSetPermissionService {
    
    private final ItemTypeSetRepository itemTypeSetRepository;
    private final WorkerPermissionRepository workerPermissionRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldEditorPermissionRepository fieldEditorPermissionRepository;
    private final CreatorPermissionRepository creatorPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final ItemTypePermissionService itemTypePermissionService;
    private final RoleRepository roleRepository;
    private final GrantRepository grantRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    
    /**
     * Crea automaticamente tutte le permissions per un ItemTypeSet
     */
    public void createPermissionsForItemTypeSet(Long itemTypeSetId, Tenant tenant) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findById(itemTypeSetId)
                .orElseThrow(() -> new RuntimeException("ItemTypeSet not found"));
        
        // Crea le permissions per ogni ItemTypeConfiguration
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            itemTypePermissionService.createPermissionsForItemTypeConfiguration(config);
        }
        
        // Crea una grant vuota di default per ogni permission
        createDefaultGrantsForItemTypeSet(itemTypeSetId, tenant);
    }
    
    /**
     * Crea una grant vuota di default per ogni permission dell'ItemTypeSet
     */
    private void createDefaultGrantsForItemTypeSet(Long itemTypeSetId, Tenant tenant) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByIdWithAllRelations(itemTypeSetId, tenant)
                .orElseThrow(() -> new RuntimeException("ItemTypeSet not found"));
        
        // Crea grant per Worker permissions
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<WorkerPermission> workerPermissions = workerPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (WorkerPermission perm : workerPermissions) {
                if (perm.getAssignedGrants().isEmpty()) {
                    Grant defaultGrant = new Grant();
                    defaultGrant = grantRepository.save(defaultGrant);
                    perm.getAssignedGrants().add(defaultGrant);
                    workerPermissionRepository.save(perm);
                }
            }
        }
        
        // Crea grant per StatusOwner permissions
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<StatusOwnerPermission> statusOwnerPermissions = statusOwnerPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (StatusOwnerPermission perm : statusOwnerPermissions) {
                if (perm.getAssignedGrants().isEmpty()) {
                    Grant defaultGrant = new Grant();
                    defaultGrant = grantRepository.save(defaultGrant);
                    perm.getAssignedGrants().add(defaultGrant);
                    statusOwnerPermissionRepository.save(perm);
                }
            }
            
            // Crea grant per FieldEditor permissions
            List<FieldEditorPermission> fieldEditorPermissions = fieldEditorPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (FieldEditorPermission perm : fieldEditorPermissions) {
                if (perm.getAssignedGrants().isEmpty()) {
                    Grant defaultGrant = new Grant();
                    defaultGrant = grantRepository.save(defaultGrant);
                    perm.getAssignedGrants().add(defaultGrant);
                    fieldEditorPermissionRepository.save(perm);
                }
            }
            
            // Crea grant per Creator permissions
            List<CreatorPermission> creatorPermissions = creatorPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (CreatorPermission perm : creatorPermissions) {
                if (perm.getAssignedGrants().isEmpty()) {
                    Grant defaultGrant = new Grant();
                    defaultGrant = grantRepository.save(defaultGrant);
                    perm.getAssignedGrants().add(defaultGrant);
                    creatorPermissionRepository.save(perm);
                }
            }
            
            // Crea grant per Executor permissions
            List<ExecutorPermission> executorPermissions = executorPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (ExecutorPermission perm : executorPermissions) {
                if (perm.getAssignedGrants().isEmpty()) {
                    Grant defaultGrant = new Grant();
                    defaultGrant = grantRepository.save(defaultGrant);
                    perm.getAssignedGrants().add(defaultGrant);
                    executorPermissionRepository.save(perm);
                }
            }
            
            // Crea grant per FieldStatus permissions (Editor e Viewer)
            List<FieldStatusPermission> editorPermissions = fieldStatusPermissionRepository
                .findAllByItemTypeConfigurationAndPermissionType(config, FieldStatusPermission.PermissionType.EDITOR);
            
            for (FieldStatusPermission perm : editorPermissions) {
                if (perm.getAssignedGrants().isEmpty()) {
                    Grant defaultGrant = new Grant();
                    defaultGrant = grantRepository.save(defaultGrant);
                    perm.getAssignedGrants().add(defaultGrant);
                    fieldStatusPermissionRepository.save(perm);
                }
            }
            
            List<FieldStatusPermission> viewerPermissions = fieldStatusPermissionRepository
                .findAllByItemTypeConfigurationAndPermissionType(config, FieldStatusPermission.PermissionType.VIEWER);
            
            for (FieldStatusPermission perm : viewerPermissions) {
                if (perm.getAssignedGrants().isEmpty()) {
                    Grant defaultGrant = new Grant();
                    defaultGrant = grantRepository.save(defaultGrant);
                    perm.getAssignedGrants().add(defaultGrant);
                    fieldStatusPermissionRepository.save(perm);
                }
            }
        }
    }
    
    /**
     * Ottiene tutte le permissions per un ItemTypeSet, raggruppate per tipo
     */
    public Map<String, List<Map<String, Object>>> getPermissionsByItemTypeSet(Long itemTypeSetId, Tenant tenant) {
        try {
            ItemTypeSet itemTypeSet = itemTypeSetRepository.findByIdWithAllRelations(itemTypeSetId, tenant)
                    .orElseThrow(() -> new RuntimeException("ItemTypeSet not found"));
            
            Map<String, List<Map<String, Object>>> result = new HashMap<>();
        
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
                worker.put("assignedGrantsCount", perm.getAssignedGrants().size());
                
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
                
                List<Map<String, Object>> assignedGrants = new ArrayList<>();
                for (Grant grant : perm.getAssignedGrants()) {
                    Map<String, Object> grantMap = new HashMap<>();
                    grantMap.put("id", grant.getId());
                    assignedGrants.add(grantMap);
                }
                worker.put("assignedGrants", assignedGrants);
                
                // Aggiungi utenti e gruppi assegnati dalle grants
                List<Map<String, Object>> assignedUsers = new ArrayList<>();
                List<Map<String, Object>> assignedGroups = new ArrayList<>();
                List<Map<String, Object>> deniedUsers = new ArrayList<>();
                List<Map<String, Object>> deniedGroups = new ArrayList<>();
                
                for (Grant grant : perm.getAssignedGrants()) {
                    for (User user : grant.getUsers()) {
                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("id", user.getId());
                        userMap.put("username", user.getUsername());
                        userMap.put("fullName", user.getFullName());
                        assignedUsers.add(userMap);
                    }
                    for (Group group : grant.getGroups()) {
                        Map<String, Object> groupMap = new HashMap<>();
                        groupMap.put("id", group.getId());
                        groupMap.put("name", group.getName());
                        groupMap.put("description", group.getDescription());
                        assignedGroups.add(groupMap);
                    }
                    for (User user : grant.getNegatedUsers()) {
                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("id", user.getId());
                        userMap.put("username", user.getUsername());
                        userMap.put("fullName", user.getFullName());
                        deniedUsers.add(userMap);
                    }
                    for (Group group : grant.getNegatedGroups()) {
                        Map<String, Object> groupMap = new HashMap<>();
                        groupMap.put("id", group.getId());
                        groupMap.put("name", group.getName());
                        groupMap.put("description", group.getDescription());
                        deniedGroups.add(groupMap);
                    }
                }
                
                worker.put("assignedUsers", assignedUsers);
                worker.put("assignedGroups", assignedGroups);
                worker.put("deniedUsers", deniedUsers);
                worker.put("deniedGroups", deniedGroups);
                
                workers.add(worker);
            }
        }
        result.put("WORKER", workers);
        
        // StatusOwner permissions
        List<Map<String, Object>> statusOwners = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<StatusOwnerPermission> permissions = statusOwnerPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (StatusOwnerPermission perm : permissions) {
                Map<String, Object> statusOwner = new HashMap<>();
                statusOwner.put("id", perm.getId());
                statusOwner.put("name", "StatusOwner");
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
                        statusOwner.put("assignedGrantsCount", perm.getAssignedGrants().size());
                        
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
                        
                        List<Map<String, Object>> assignedGrants = new ArrayList<>();
                        for (Grant grant : perm.getAssignedGrants()) {
                            Map<String, Object> grantMap = new HashMap<>();
                            grantMap.put("id", grant.getId());
                            // TODO: Aggiungere dettagli della grant se necessario
                            assignedGrants.add(grantMap);
                        }
                        statusOwner.put("assignedGrants", assignedGrants);
                        
                        // Aggiungi utenti e gruppi assegnati dalle grants
                        List<Map<String, Object>> assignedUsers = new ArrayList<>();
                        List<Map<String, Object>> assignedGroups = new ArrayList<>();
                        List<Map<String, Object>> deniedUsers = new ArrayList<>();
                        List<Map<String, Object>> deniedGroups = new ArrayList<>();
                        
                        for (Grant grant : perm.getAssignedGrants()) {
                            // Utenti assegnati
                            for (User user : grant.getUsers()) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("id", user.getId());
                                userMap.put("username", user.getUsername());
                                userMap.put("fullName", user.getFullName());
                                assignedUsers.add(userMap);
                            }
                            
                            // Gruppi assegnati
                            for (Group group : grant.getGroups()) {
                                Map<String, Object> groupMap = new HashMap<>();
                                groupMap.put("id", group.getId());
                                groupMap.put("name", group.getName());
                                groupMap.put("description", group.getDescription());
                                assignedGroups.add(groupMap);
                            }
                            
                            // Utenti negati
                            for (User user : grant.getNegatedUsers()) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("id", user.getId());
                                userMap.put("username", user.getUsername());
                                userMap.put("fullName", user.getFullName());
                                deniedUsers.add(userMap);
                            }
                            
                            // Gruppi negati
                            for (Group group : grant.getNegatedGroups()) {
                                Map<String, Object> groupMap = new HashMap<>();
                                groupMap.put("id", group.getId());
                                groupMap.put("name", group.getName());
                                groupMap.put("description", group.getDescription());
                                deniedGroups.add(groupMap);
                            }
                        }
                        
                        statusOwner.put("assignedUsers", assignedUsers);
                        statusOwner.put("assignedGroups", assignedGroups);
                        statusOwner.put("deniedUsers", deniedUsers);
                        statusOwner.put("deniedGroups", deniedGroups);
                statusOwners.add(statusOwner);
            }
        }
        result.put("STATUS_OWNER", statusOwners);
        
        // FieldEditor permissions
        List<Map<String, Object>> fieldEditors = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<FieldEditorPermission> permissions = fieldEditorPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (FieldEditorPermission perm : permissions) {
                Map<String, Object> fieldEditor = new HashMap<>();
                fieldEditor.put("id", perm.getId());
                fieldEditor.put("name", "FieldEditors");
                Map<String, Object> fieldConfigMap = new HashMap<>();
                fieldConfigMap.put("id", perm.getFieldConfiguration().getId());
                fieldConfigMap.put("name", perm.getFieldConfiguration().getField() != null ? 
                    perm.getFieldConfiguration().getField().getName() : perm.getFieldConfiguration().getName());
                fieldConfigMap.put("fieldType", perm.getFieldConfiguration().getFieldType());
                fieldEditor.put("fieldConfiguration", fieldConfigMap);
                
                Map<String, Object> itemTypeMap3 = new HashMap<>();
                itemTypeMap3.put("id", config.getItemType().getId());
                itemTypeMap3.put("name", config.getItemType().getName());
                fieldEditor.put("itemType", itemTypeMap3);
                        fieldEditor.put("assignedRolesCount", perm.getAssignedRoles().size());
                        fieldEditor.put("assignedGrantsCount", perm.getAssignedGrants().size());
                        
                        // Aggiungi i dati delle assegnazioni esistenti
                        List<Map<String, Object>> assignedRoles = new ArrayList<>();
                        for (Role role : perm.getAssignedRoles()) {
                            Map<String, Object> roleMap = new HashMap<>();
                            roleMap.put("id", role.getId());
                            roleMap.put("name", role.getName());
                            roleMap.put("description", role.getDescription());
                            assignedRoles.add(roleMap);
                        }
                        fieldEditor.put("assignedRoles", assignedRoles);
                        
                        List<Map<String, Object>> assignedGrants = new ArrayList<>();
                        for (Grant grant : perm.getAssignedGrants()) {
                            Map<String, Object> grantMap = new HashMap<>();
                            grantMap.put("id", grant.getId());
                            // TODO: Aggiungere dettagli della grant se necessario
                            assignedGrants.add(grantMap);
                        }
                        fieldEditor.put("assignedGrants", assignedGrants);
                        
                        // Aggiungi utenti e gruppi assegnati dalle grants
                        List<Map<String, Object>> assignedUsers = new ArrayList<>();
                        List<Map<String, Object>> assignedGroups = new ArrayList<>();
                        List<Map<String, Object>> deniedUsers = new ArrayList<>();
                        List<Map<String, Object>> deniedGroups = new ArrayList<>();
                        
                        for (Grant grant : perm.getAssignedGrants()) {
                            // Utenti assegnati
                            for (User user : grant.getUsers()) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("id", user.getId());
                                userMap.put("username", user.getUsername());
                                userMap.put("fullName", user.getFullName());
                                assignedUsers.add(userMap);
                            }
                            
                            // Gruppi assegnati
                            for (Group group : grant.getGroups()) {
                                Map<String, Object> groupMap = new HashMap<>();
                                groupMap.put("id", group.getId());
                                groupMap.put("name", group.getName());
                                groupMap.put("description", group.getDescription());
                                assignedGroups.add(groupMap);
                            }
                            
                            // Utenti negati
                            for (User user : grant.getNegatedUsers()) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("id", user.getId());
                                userMap.put("username", user.getUsername());
                                userMap.put("fullName", user.getFullName());
                                deniedUsers.add(userMap);
                            }
                            
                            // Gruppi negati
                            for (Group group : grant.getNegatedGroups()) {
                                Map<String, Object> groupMap = new HashMap<>();
                                groupMap.put("id", group.getId());
                                groupMap.put("name", group.getName());
                                groupMap.put("description", group.getDescription());
                                deniedGroups.add(groupMap);
                            }
                        }
                        
                        fieldEditor.put("assignedUsers", assignedUsers);
                        fieldEditor.put("assignedGroups", assignedGroups);
                        fieldEditor.put("deniedUsers", deniedUsers);
                        fieldEditor.put("deniedGroups", deniedGroups);
                fieldEditors.add(fieldEditor);
            }
        }
        result.put("FIELD_EDITOR", fieldEditors);
        
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
                        creator.put("assignedGrantsCount", perm.getAssignedGrants().size());
                        
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
                        
                        List<Map<String, Object>> assignedGrants = new ArrayList<>();
                        for (Grant grant : perm.getAssignedGrants()) {
                            Map<String, Object> grantMap = new HashMap<>();
                            grantMap.put("id", grant.getId());
                            // TODO: Aggiungere dettagli della grant se necessario
                            assignedGrants.add(grantMap);
                        }
                        creator.put("assignedGrants", assignedGrants);
                        
                        // Aggiungi utenti e gruppi assegnati dalle grants
                        List<Map<String, Object>> assignedUsers = new ArrayList<>();
                        List<Map<String, Object>> assignedGroups = new ArrayList<>();
                        List<Map<String, Object>> deniedUsers = new ArrayList<>();
                        List<Map<String, Object>> deniedGroups = new ArrayList<>();
                        
                        for (Grant grant : perm.getAssignedGrants()) {
                            // Utenti assegnati
                            for (User user : grant.getUsers()) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("id", user.getId());
                                userMap.put("username", user.getUsername());
                                userMap.put("fullName", user.getFullName());
                                assignedUsers.add(userMap);
                            }
                            
                            // Gruppi assegnati
                            for (Group group : grant.getGroups()) {
                                Map<String, Object> groupMap = new HashMap<>();
                                groupMap.put("id", group.getId());
                                groupMap.put("name", group.getName());
                                groupMap.put("description", group.getDescription());
                                assignedGroups.add(groupMap);
                            }
                            
                            // Utenti negati
                            for (User user : grant.getNegatedUsers()) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("id", user.getId());
                                userMap.put("username", user.getUsername());
                                userMap.put("fullName", user.getFullName());
                                deniedUsers.add(userMap);
                            }
                            
                            // Gruppi negati
                            for (Group group : grant.getNegatedGroups()) {
                                Map<String, Object> groupMap = new HashMap<>();
                                groupMap.put("id", group.getId());
                                groupMap.put("name", group.getName());
                                groupMap.put("description", group.getDescription());
                                deniedGroups.add(groupMap);
                            }
                        }
                        
                        creator.put("assignedUsers", assignedUsers);
                        creator.put("assignedGroups", assignedGroups);
                        creator.put("deniedUsers", deniedUsers);
                        creator.put("deniedGroups", deniedGroups);
                creators.add(creator);
            }
        }
        result.put("CREATOR", creators);
        
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
                        executor.put("assignedGrantsCount", perm.getAssignedGrants().size());
                        
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
                        
                        List<Map<String, Object>> assignedGrants = new ArrayList<>();
                        for (Grant grant : perm.getAssignedGrants()) {
                            Map<String, Object> grantMap = new HashMap<>();
                            grantMap.put("id", grant.getId());
                            // TODO: Aggiungere dettagli della grant se necessario
                            assignedGrants.add(grantMap);
                        }
                        executor.put("assignedGrants", assignedGrants);
                        
                        // Aggiungi utenti e gruppi assegnati dalle grants
                        List<Map<String, Object>> assignedUsers = new ArrayList<>();
                        List<Map<String, Object>> assignedGroups = new ArrayList<>();
                        List<Map<String, Object>> deniedUsers = new ArrayList<>();
                        List<Map<String, Object>> deniedGroups = new ArrayList<>();
                        
                        for (Grant grant : perm.getAssignedGrants()) {
                            // Utenti assegnati
                            for (User user : grant.getUsers()) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("id", user.getId());
                                userMap.put("username", user.getUsername());
                                userMap.put("fullName", user.getFullName());
                                assignedUsers.add(userMap);
                            }
                            
                            // Gruppi assegnati
                            for (Group group : grant.getGroups()) {
                                Map<String, Object> groupMap = new HashMap<>();
                                groupMap.put("id", group.getId());
                                groupMap.put("name", group.getName());
                                groupMap.put("description", group.getDescription());
                                assignedGroups.add(groupMap);
                            }
                            
                            // Utenti negati
                            for (User user : grant.getNegatedUsers()) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("id", user.getId());
                                userMap.put("username", user.getUsername());
                                userMap.put("fullName", user.getFullName());
                                deniedUsers.add(userMap);
                            }
                            
                            // Gruppi negati
                            for (Group group : grant.getNegatedGroups()) {
                                Map<String, Object> groupMap = new HashMap<>();
                                groupMap.put("id", group.getId());
                                groupMap.put("name", group.getName());
                                groupMap.put("description", group.getDescription());
                                deniedGroups.add(groupMap);
                            }
                        }
                        
                        executor.put("assignedUsers", assignedUsers);
                        executor.put("assignedGroups", assignedGroups);
                        executor.put("deniedUsers", deniedUsers);
                        executor.put("deniedGroups", deniedGroups);
                executors.add(executor);
            }
        }
        result.put("EXECUTOR", executors);
        
        // Editor permissions
        List<Map<String, Object>> editors = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<FieldStatusPermission> permissions = fieldStatusPermissionRepository
                .findAllByItemTypeConfigurationAndPermissionType(config, FieldStatusPermission.PermissionType.EDITOR);
            
            for (FieldStatusPermission perm : permissions) {
                Map<String, Object> editor = new HashMap<>();
                editor.put("id", perm.getId());
                editor.put("name", "Editors");
                Map<String, Object> fieldConfigMap2 = new HashMap<>();
                fieldConfigMap2.put("id", perm.getFieldConfiguration().getId());
                fieldConfigMap2.put("name", perm.getFieldConfiguration().getField() != null ? 
                    perm.getFieldConfiguration().getField().getName() : perm.getFieldConfiguration().getName());
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
                        editor.put("assignedGrantsCount", perm.getAssignedGrants().size());
                        
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
                        
                        List<Map<String, Object>> assignedGrants = new ArrayList<>();
                        for (Grant grant : perm.getAssignedGrants()) {
                            Map<String, Object> grantMap = new HashMap<>();
                            grantMap.put("id", grant.getId());
                            // TODO: Aggiungere dettagli della grant se necessario
                            assignedGrants.add(grantMap);
                        }
                        editor.put("assignedGrants", assignedGrants);
                        
                        // Aggiungi utenti e gruppi assegnati dalle grants
                        List<Map<String, Object>> assignedUsers = new ArrayList<>();
                        List<Map<String, Object>> assignedGroups = new ArrayList<>();
                        List<Map<String, Object>> deniedUsers = new ArrayList<>();
                        List<Map<String, Object>> deniedGroups = new ArrayList<>();
                        
                        for (Grant grant : perm.getAssignedGrants()) {
                            // Utenti assegnati
                            for (User user : grant.getUsers()) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("id", user.getId());
                                userMap.put("username", user.getUsername());
                                userMap.put("fullName", user.getFullName());
                                assignedUsers.add(userMap);
                            }
                            
                            // Gruppi assegnati
                            for (Group group : grant.getGroups()) {
                                Map<String, Object> groupMap = new HashMap<>();
                                groupMap.put("id", group.getId());
                                groupMap.put("name", group.getName());
                                groupMap.put("description", group.getDescription());
                                assignedGroups.add(groupMap);
                            }
                            
                            // Utenti negati
                            for (User user : grant.getNegatedUsers()) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("id", user.getId());
                                userMap.put("username", user.getUsername());
                                userMap.put("fullName", user.getFullName());
                                deniedUsers.add(userMap);
                            }
                            
                            // Gruppi negati
                            for (Group group : grant.getNegatedGroups()) {
                                Map<String, Object> groupMap = new HashMap<>();
                                groupMap.put("id", group.getId());
                                groupMap.put("name", group.getName());
                                groupMap.put("description", group.getDescription());
                                deniedGroups.add(groupMap);
                            }
                        }
                        
                        editor.put("assignedUsers", assignedUsers);
                        editor.put("assignedGroups", assignedGroups);
                        editor.put("deniedUsers", deniedUsers);
                        editor.put("deniedGroups", deniedGroups);
                editors.add(editor);
            }
        }
        result.put("EDITOR", editors);
        
        // Viewer permissions
        List<Map<String, Object>> viewers = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<FieldStatusPermission> permissions = fieldStatusPermissionRepository
                .findAllByItemTypeConfigurationAndPermissionType(config, FieldStatusPermission.PermissionType.VIEWER);
            
            for (FieldStatusPermission perm : permissions) {
                Map<String, Object> viewer = new HashMap<>();
                viewer.put("id", perm.getId());
                viewer.put("name", "Viewers");
                Map<String, Object> fieldConfigMap3 = new HashMap<>();
                fieldConfigMap3.put("id", perm.getFieldConfiguration().getId());
                fieldConfigMap3.put("name", perm.getFieldConfiguration().getField() != null ? 
                    perm.getFieldConfiguration().getField().getName() : perm.getFieldConfiguration().getName());
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
                        viewer.put("assignedGrantsCount", perm.getAssignedGrants().size());
                        
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
                        
                        List<Map<String, Object>> assignedGrants = new ArrayList<>();
                        for (Grant grant : perm.getAssignedGrants()) {
                            Map<String, Object> grantMap = new HashMap<>();
                            grantMap.put("id", grant.getId());
                            // TODO: Aggiungere dettagli della grant se necessario
                            assignedGrants.add(grantMap);
                        }
                        viewer.put("assignedGrants", assignedGrants);
                        
                        // Aggiungi utenti e gruppi assegnati dalle grants
                        List<Map<String, Object>> assignedUsers = new ArrayList<>();
                        List<Map<String, Object>> assignedGroups = new ArrayList<>();
                        List<Map<String, Object>> deniedUsers = new ArrayList<>();
                        List<Map<String, Object>> deniedGroups = new ArrayList<>();
                        
                        for (Grant grant : perm.getAssignedGrants()) {
                            // Utenti assegnati
                            for (User user : grant.getUsers()) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("id", user.getId());
                                userMap.put("username", user.getUsername());
                                userMap.put("fullName", user.getFullName());
                                assignedUsers.add(userMap);
                            }
                            
                            // Gruppi assegnati
                            for (Group group : grant.getGroups()) {
                                Map<String, Object> groupMap = new HashMap<>();
                                groupMap.put("id", group.getId());
                                groupMap.put("name", group.getName());
                                groupMap.put("description", group.getDescription());
                                assignedGroups.add(groupMap);
                            }
                            
                            // Utenti negati
                            for (User user : grant.getNegatedUsers()) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("id", user.getId());
                                userMap.put("username", user.getUsername());
                                userMap.put("fullName", user.getFullName());
                                deniedUsers.add(userMap);
                            }
                            
                            // Gruppi negati
                            for (Group group : grant.getNegatedGroups()) {
                                Map<String, Object> groupMap = new HashMap<>();
                                groupMap.put("id", group.getId());
                                groupMap.put("name", group.getName());
                                groupMap.put("description", group.getDescription());
                                deniedGroups.add(groupMap);
                            }
                        }
                        
                        viewer.put("assignedUsers", assignedUsers);
                        viewer.put("assignedGroups", assignedGroups);
                        viewer.put("deniedUsers", deniedUsers);
                        viewer.put("deniedGroups", deniedGroups);
                viewers.add(viewer);
            }
        }
        result.put("VIEWER", viewers);
        
        return result;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error retrieving permissions: " + e.getMessage(), e);
        }
    }
    
    /**
     * Assegna un ruolo a una permission
     */
    public void assignRoleToPermission(Long permissionId, Long roleId) {
        // Trova la permission per ID
        WorkerPermission workerPermission = workerPermissionRepository.findById(permissionId).orElse(null);
        if (workerPermission != null) {
            Role role = roleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));
            workerPermission.getAssignedRoles().add(role);
            workerPermissionRepository.save(workerPermission);
            return;
        }
        
        StatusOwnerPermission statusOwnerPermission = statusOwnerPermissionRepository.findById(permissionId).orElse(null);
        if (statusOwnerPermission != null) {
            Role role = roleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));
            statusOwnerPermission.getAssignedRoles().add(role);
            statusOwnerPermissionRepository.save(statusOwnerPermission);
            return;
        }
        
        FieldEditorPermission fieldEditorPermission = fieldEditorPermissionRepository.findById(permissionId).orElse(null);
        if (fieldEditorPermission != null) {
            Role role = roleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));
            fieldEditorPermission.getAssignedRoles().add(role);
            fieldEditorPermissionRepository.save(fieldEditorPermission);
            return;
        }
        
        CreatorPermission creatorPermission = creatorPermissionRepository.findById(permissionId).orElse(null);
        if (creatorPermission != null) {
            Role role = roleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));
            creatorPermission.getAssignedRoles().add(role);
            creatorPermissionRepository.save(creatorPermission);
            return;
        }
        
        ExecutorPermission executorPermission = executorPermissionRepository.findById(permissionId).orElse(null);
        if (executorPermission != null) {
            Role role = roleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));
            executorPermission.getAssignedRoles().add(role);
            executorPermissionRepository.save(executorPermission);
            return;
        }
        
        FieldStatusPermission fieldStatusPermission = fieldStatusPermissionRepository.findById(permissionId).orElse(null);
        if (fieldStatusPermission != null) {
            Role role = roleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));
            fieldStatusPermission.getAssignedRoles().add(role);
            fieldStatusPermissionRepository.save(fieldStatusPermission);
            return;
        }
        
        throw new RuntimeException("Permission not found with ID: " + permissionId);
    }
    
    /**
     * Assegna una grant a una permission
     */
    public void assignGrantToPermission(Long permissionId, Map<String, Object> grantData) {
        // Trova la permission per ID
        WorkerPermission workerPermission = workerPermissionRepository.findById(permissionId).orElse(null);
        StatusOwnerPermission statusOwnerPermission = null;
        FieldEditorPermission fieldEditorPermission = null;
        CreatorPermission creatorPermission = null;
        ExecutorPermission executorPermission = null;
        FieldStatusPermission fieldStatusPermission = null;
        
        if (workerPermission == null) {
            statusOwnerPermission = statusOwnerPermissionRepository.findById(permissionId).orElse(null);
        }
        
        if (statusOwnerPermission == null) {
            fieldEditorPermission = fieldEditorPermissionRepository.findById(permissionId).orElse(null);
        }
        if (statusOwnerPermission == null && fieldEditorPermission == null) {
            creatorPermission = creatorPermissionRepository.findById(permissionId).orElse(null);
        }
        if (statusOwnerPermission == null && fieldEditorPermission == null && creatorPermission == null) {
            executorPermission = executorPermissionRepository.findById(permissionId).orElse(null);
        }
        if (statusOwnerPermission == null && fieldEditorPermission == null && creatorPermission == null && executorPermission == null) {
            fieldStatusPermission = fieldStatusPermissionRepository.findById(permissionId).orElse(null);
        }
        
        if (workerPermission == null && statusOwnerPermission == null && fieldEditorPermission == null && creatorPermission == null && executorPermission == null && fieldStatusPermission == null) {
            throw new RuntimeException("Permission not found with ID: " + permissionId);
        }
        
        // Trova o crea una grant esistente per questa permission
        Grant grant = null;
        if (workerPermission != null && !workerPermission.getAssignedGrants().isEmpty()) {
            grant = workerPermission.getAssignedGrants().iterator().next();
        } else if (statusOwnerPermission != null && !statusOwnerPermission.getAssignedGrants().isEmpty()) {
            grant = statusOwnerPermission.getAssignedGrants().iterator().next();
        } else if (fieldEditorPermission != null && !fieldEditorPermission.getAssignedGrants().isEmpty()) {
            grant = fieldEditorPermission.getAssignedGrants().iterator().next();
        } else if (creatorPermission != null && !creatorPermission.getAssignedGrants().isEmpty()) {
            grant = creatorPermission.getAssignedGrants().iterator().next();
        } else if (executorPermission != null && !executorPermission.getAssignedGrants().isEmpty()) {
            grant = executorPermission.getAssignedGrants().iterator().next();
        } else if (fieldStatusPermission != null && !fieldStatusPermission.getAssignedGrants().isEmpty()) {
            grant = fieldStatusPermission.getAssignedGrants().iterator().next();
        }
        
        // Se non esiste una grant, creane una nuova
        boolean isNewGrant = (grant == null);
        if (grant == null) {
            grant = new Grant();
        } else {
            // Pulisci i dati esistenti e salva per aggiornare le relazioni
            grant.getUsers().clear();
            grant.getGroups().clear();
            grant.getNegatedUsers().clear();
            grant.getNegatedGroups().clear();
            // Salva la grant vuota per aggiornare le relazioni
            grant = grantRepository.save(grant);
        }
        
        // Popola gli utenti assegnati
        if (grantData.containsKey("users") && grantData.get("users") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> usersData = (List<Map<String, Object>>) grantData.get("users");
            for (Map<String, Object> userData : usersData) {
                Long userId = ((Number) userData.get("id")).longValue();
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    grant.getUsers().add(user);
                }
            }
        }
        
        // Popola i gruppi assegnati
        if (grantData.containsKey("groups") && grantData.get("groups") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groupsData = (List<Map<String, Object>>) grantData.get("groups");
            for (Map<String, Object> groupData : groupsData) {
                Long groupId = ((Number) groupData.get("id")).longValue();
                Group group = groupRepository.findById(groupId).orElse(null);
                if (group != null) {
                    grant.getGroups().add(group);
                }
            }
        }
        
        // Popola gli utenti negati
        if (grantData.containsKey("deniedUsers") && grantData.get("deniedUsers") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> deniedUsersData = (List<Map<String, Object>>) grantData.get("deniedUsers");
            for (Map<String, Object> userData : deniedUsersData) {
                Long userId = ((Number) userData.get("id")).longValue();
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    grant.getNegatedUsers().add(user);
                }
            }
        }
        
        // Popola i gruppi negati
        if (grantData.containsKey("deniedGroups") && grantData.get("deniedGroups") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> deniedGroupsData = (List<Map<String, Object>>) grantData.get("deniedGroups");
            for (Map<String, Object> groupData : deniedGroupsData) {
                Long groupId = ((Number) groupData.get("id")).longValue();
                Group group = groupRepository.findById(groupId).orElse(null);
                if (group != null) {
                    grant.getNegatedGroups().add(group);
                }
            }
        }
        
        grant = grantRepository.save(grant);
        
        // Assegna la grant alla permission solo se è nuova
        if (isNewGrant) {
            if (workerPermission != null) {
                workerPermission.getAssignedGrants().add(grant);
                workerPermissionRepository.save(workerPermission);
            } else if (statusOwnerPermission != null) {
                statusOwnerPermission.getAssignedGrants().add(grant);
                statusOwnerPermissionRepository.save(statusOwnerPermission);
            } else if (fieldEditorPermission != null) {
                fieldEditorPermission.getAssignedGrants().add(grant);
                fieldEditorPermissionRepository.save(fieldEditorPermission);
            } else if (creatorPermission != null) {
                creatorPermission.getAssignedGrants().add(grant);
                creatorPermissionRepository.save(creatorPermission);
            } else if (executorPermission != null) {
                executorPermission.getAssignedGrants().add(grant);
                executorPermissionRepository.save(executorPermission);
            } else if (fieldStatusPermission != null) {
                fieldStatusPermission.getAssignedGrants().add(grant);
                fieldStatusPermissionRepository.save(fieldStatusPermission);
            }
        } else {
            // Se è una grant esistente, salva solo la permission per aggiornare le relazioni
            if (workerPermission != null) {
                workerPermissionRepository.save(workerPermission);
            } else if (statusOwnerPermission != null) {
                statusOwnerPermissionRepository.save(statusOwnerPermission);
            } else if (fieldEditorPermission != null) {
                fieldEditorPermissionRepository.save(fieldEditorPermission);
            } else if (creatorPermission != null) {
                creatorPermissionRepository.save(creatorPermission);
            } else if (executorPermission != null) {
                executorPermissionRepository.save(executorPermission);
            } else if (fieldStatusPermission != null) {
                fieldStatusPermissionRepository.save(fieldStatusPermission);
            }
        }
    }
    
    /**
     * Rimuove un ruolo da una permission
     */
    public void removeRoleFromPermission(Long permissionId, Long roleId) {
        // Trova la permission per ID
        WorkerPermission workerPermission = workerPermissionRepository.findById(permissionId).orElse(null);
        if (workerPermission != null) {
            workerPermission.getAssignedRoles().removeIf(role -> role.getId().equals(roleId));
            workerPermissionRepository.save(workerPermission);
            return;
        }
        
        StatusOwnerPermission statusOwnerPermission = statusOwnerPermissionRepository.findById(permissionId).orElse(null);
        if (statusOwnerPermission != null) {
            statusOwnerPermission.getAssignedRoles().removeIf(role -> role.getId().equals(roleId));
            statusOwnerPermissionRepository.save(statusOwnerPermission);
            return;
        }
        
        FieldEditorPermission fieldEditorPermission = fieldEditorPermissionRepository.findById(permissionId).orElse(null);
        if (fieldEditorPermission != null) {
            fieldEditorPermission.getAssignedRoles().removeIf(role -> role.getId().equals(roleId));
            fieldEditorPermissionRepository.save(fieldEditorPermission);
            return;
        }
        
        CreatorPermission creatorPermission = creatorPermissionRepository.findById(permissionId).orElse(null);
        if (creatorPermission != null) {
            creatorPermission.getAssignedRoles().removeIf(role -> role.getId().equals(roleId));
            creatorPermissionRepository.save(creatorPermission);
            return;
        }
        
        ExecutorPermission executorPermission = executorPermissionRepository.findById(permissionId).orElse(null);
        if (executorPermission != null) {
            executorPermission.getAssignedRoles().removeIf(role -> role.getId().equals(roleId));
            executorPermissionRepository.save(executorPermission);
            return;
        }
        
        FieldStatusPermission fieldStatusPermission = fieldStatusPermissionRepository.findById(permissionId).orElse(null);
        if (fieldStatusPermission != null) {
            fieldStatusPermission.getAssignedRoles().removeIf(role -> role.getId().equals(roleId));
            fieldStatusPermissionRepository.save(fieldStatusPermission);
            return;
        }
        
        throw new RuntimeException("Permission not found with ID: " + permissionId);
    }
}

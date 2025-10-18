package com.example.demo.service;

import com.example.demo.entity.*;
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
    private final GrantRepository grantRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final EntityManager entityManager;
    
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
            
            // Crea grant per FieldOwner permissions
            List<FieldOwnerPermission> fieldOwnerPermissions = fieldOwnerPermissionRepository
                .findAllByItemTypeConfiguration(config);

            for (FieldOwnerPermission perm : fieldOwnerPermissions) {
                if (perm.getAssignedGrants().isEmpty()) {
                    Grant defaultGrant = new Grant();
                    defaultGrant = grantRepository.save(defaultGrant);
                    perm.getAssignedGrants().add(defaultGrant);
                    fieldOwnerPermissionRepository.save(perm);
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
                .findAllByItemTypeConfigurationAndPermissionType(config, FieldStatusPermission.PermissionType.EDITORS);
            
            for (FieldStatusPermission perm : editorPermissions) {
                if (perm.getAssignedGrants().isEmpty()) {
                    Grant defaultGrant = new Grant();
                    defaultGrant = grantRepository.save(defaultGrant);
                    perm.getAssignedGrants().add(defaultGrant);
                    fieldStatusPermissionRepository.save(perm);
                }
            }
            
            List<FieldStatusPermission> viewerPermissions = fieldStatusPermissionRepository
                .findAllByItemTypeConfigurationAndPermissionType(config, FieldStatusPermission.PermissionType.VIEWERS);
            
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
                worker.put("assignedGrantsCount", perm.getAssignedGrants().size());
                
                // Calcola se ci sono effettivamente assegnazioni (ruoli, utenti o gruppi)
                boolean hasAssignments = !perm.getAssignedRoles().isEmpty(); // Controlla prima i ruoli
                
                if (!hasAssignments) {
                    // Se non ci sono ruoli, controlla utenti e gruppi nelle grants
                    for (Grant grant : perm.getAssignedGrants()) {
                        if (!grant.getUsers().isEmpty() || !grant.getGroups().isEmpty() || 
                            !grant.getNegatedUsers().isEmpty() || !grant.getNegatedGroups().isEmpty()) {
                            hasAssignments = true;
                            break;
                        }
                    }
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
        result.put("WORKERS", workers);
        
        // StatusOwner permissions
        List<Map<String, Object>> statusOwners = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<StatusOwnerPermission> permissions = statusOwnerPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (StatusOwnerPermission perm : permissions) {
                Map<String, Object> statusOwner = new HashMap<>();
                statusOwner.put("id", perm.getId());
                statusOwner.put("name", "StatusOwners");
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
                        
                        // Calcola se ci sono effettivamente assegnazioni (ruoli, utenti o gruppi)
                        boolean hasAssignments = !perm.getAssignedRoles().isEmpty(); // Controlla prima i ruoli
                        
                        if (!hasAssignments) {
                            // Se non ci sono ruoli, controlla utenti e gruppi nelle grants
                            for (Grant grant : perm.getAssignedGrants()) {
                                if (!grant.getUsers().isEmpty() || !grant.getGroups().isEmpty() || 
                                    !grant.getNegatedUsers().isEmpty() || !grant.getNegatedGroups().isEmpty()) {
                                    hasAssignments = true;
                                    break;
                                }
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
        result.put("STATUS_OWNERS", statusOwners);
        
        // FieldOwner permissions
        List<Map<String, Object>> fieldOwners = new ArrayList<>();
        for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
            List<FieldOwnerPermission> permissions = fieldOwnerPermissionRepository
                .findAllByItemTypeConfiguration(config);
            
            for (FieldOwnerPermission perm : permissions) {
                Map<String, Object> fieldOwner = new HashMap<>();
                fieldOwner.put("id", perm.getId());
                fieldOwner.put("name", "FieldOwners");
                Map<String, Object> fieldConfigMap = new HashMap<>();
                fieldConfigMap.put("id", perm.getFieldConfiguration().getId());
                fieldConfigMap.put("name", perm.getFieldConfiguration().getField() != null ? 
                    perm.getFieldConfiguration().getField().getName() : perm.getFieldConfiguration().getName());
                fieldConfigMap.put("fieldType", perm.getFieldConfiguration().getFieldType());
                fieldOwner.put("fieldConfiguration", fieldConfigMap);
                
                Map<String, Object> itemTypeMap3 = new HashMap<>();
                itemTypeMap3.put("id", config.getItemType().getId());
                itemTypeMap3.put("name", config.getItemType().getName());
                fieldOwner.put("itemType", itemTypeMap3);
                fieldOwner.put("assignedRolesCount", perm.getAssignedRoles().size());
                fieldOwner.put("assignedGrantsCount", perm.getAssignedGrants().size());
                        
                // Calcola hasAssignments
                boolean hasAssignmentsField = !perm.getAssignedRoles().isEmpty();
                if (!hasAssignmentsField) {
                    for (Grant grant : perm.getAssignedGrants()) {
                        if (!grant.getUsers().isEmpty() || !grant.getGroups().isEmpty() ||
                            !grant.getNegatedUsers().isEmpty() || !grant.getNegatedGroups().isEmpty()) {
                            hasAssignmentsField = true;
                            break;
                        }
                    }
                }
                fieldOwner.put("hasAssignments", hasAssignmentsField);

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

                List<Map<String, Object>> assignedGrants = new ArrayList<>();
                for (Grant grant : perm.getAssignedGrants()) {
                    Map<String, Object> grantMap = new HashMap<>();
                    grantMap.put("id", grant.getId());
                    // TODO: Aggiungere dettagli della grant se necessario
                    assignedGrants.add(grantMap);
                }
                fieldOwner.put("assignedGrants", assignedGrants);

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

                fieldOwner.put("assignedUsers", assignedUsers);
                fieldOwner.put("assignedGroups", assignedGroups);
                fieldOwner.put("deniedUsers", deniedUsers);
                fieldOwner.put("deniedGroups", deniedGroups);
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
                        creator.put("assignedGrantsCount", perm.getAssignedGrants().size());
                        
                        // Calcola hasAssignments
                        boolean hasAssignmentsCreator = !perm.getAssignedRoles().isEmpty();
                        if (!hasAssignmentsCreator) {
                            for (Grant grant : perm.getAssignedGrants()) {
                                if (!grant.getUsers().isEmpty() || !grant.getGroups().isEmpty() || 
                                    !grant.getNegatedUsers().isEmpty() || !grant.getNegatedGroups().isEmpty()) {
                                    hasAssignmentsCreator = true;
                                    break;
                                }
                            }
                        }
                        creator.put("hasAssignments", hasAssignmentsCreator);
                        
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
                        executor.put("assignedGrantsCount", perm.getAssignedGrants().size());
                        
                        // Calcola hasAssignments
                        boolean hasAssignmentsExec = !perm.getAssignedRoles().isEmpty();
                        if (!hasAssignmentsExec) {
                            for (Grant grant : perm.getAssignedGrants()) {
                                if (!grant.getUsers().isEmpty() || !grant.getGroups().isEmpty() || 
                                    !grant.getNegatedUsers().isEmpty() || !grant.getNegatedGroups().isEmpty()) {
                                    hasAssignmentsExec = true;
                                    break;
                                }
                            }
                        }
                        executor.put("hasAssignments", hasAssignmentsExec);
                        
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
                        
                        // Calcola hasAssignments
                        boolean hasAssignmentsEditor = !perm.getAssignedRoles().isEmpty();
                        if (!hasAssignmentsEditor) {
                            for (Grant grant : perm.getAssignedGrants()) {
                                if (!grant.getUsers().isEmpty() || !grant.getGroups().isEmpty() || 
                                    !grant.getNegatedUsers().isEmpty() || !grant.getNegatedGroups().isEmpty()) {
                                    hasAssignmentsEditor = true;
                                    break;
                                }
                            }
                        }
                        editor.put("hasAssignments", hasAssignmentsEditor);
                        
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
                        
                        // Calcola hasAssignments
                        boolean hasAssignmentsViewer = !perm.getAssignedRoles().isEmpty();
                        if (!hasAssignmentsViewer) {
                            for (Grant grant : perm.getAssignedGrants()) {
                                if (!grant.getUsers().isEmpty() || !grant.getGroups().isEmpty() || 
                                    !grant.getNegatedUsers().isEmpty() || !grant.getNegatedGroups().isEmpty()) {
                                    hasAssignmentsViewer = true;
                                    break;
                                }
                            }
                        }
                        viewer.put("hasAssignments", hasAssignmentsViewer);
                        
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
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("Role not found"));
        
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
     * Assegna una grant a una permission
     */
    public void assignGrantToPermission(Long permissionId, String permissionType, Map<String, Object> grantData) {
        if (permissionId == null) {
            throw new ApiException("Permission ID cannot be null");
        }
        if (grantData == null) {
            throw new ApiException("Grant data cannot be null");
        }
        
        // Trova la permission per ID e TIPO
        WorkerPermission workerPermission = null;
        StatusOwnerPermission statusOwnerPermission = null;
        FieldOwnerPermission fieldOwnerPermission = null;
        CreatorPermission creatorPermission = null;
        ExecutorPermission executorPermission = null;
        FieldStatusPermission fieldStatusPermission = null;
        
        if (permissionType == null) {
            // Fallback: cerca in tutte le tabelle (vecchio comportamento)
            workerPermission = workerPermissionRepository.findById(permissionId).orElse(null);
            statusOwnerPermission = statusOwnerPermissionRepository.findById(permissionId).orElse(null);
            fieldOwnerPermission = fieldOwnerPermissionRepository.findById(permissionId).orElse(null);
            creatorPermission = creatorPermissionRepository.findById(permissionId).orElse(null);
            executorPermission = executorPermissionRepository.findById(permissionId).orElse(null);
            fieldStatusPermission = fieldStatusPermissionRepository.findById(permissionId).orElse(null);
            
            int foundCount = 0;
            if (workerPermission != null) foundCount++;
            if (statusOwnerPermission != null) foundCount++;
            if (fieldOwnerPermission != null) foundCount++;
            if (creatorPermission != null) foundCount++;
            if (executorPermission != null) foundCount++;
            if (fieldStatusPermission != null) foundCount++;
            
            if (foundCount > 1) {
                throw new ApiException("Ambiguous permission ID: " + permissionId + " - found in " + foundCount + " tables. Please provide permissionType.");
            }
            if (foundCount == 0) {
                throw new ApiException("Permission not found with ID: " + permissionId);
            }
        } else {
            // Cerca solo nella tabella specifica in base al tipo
            switch (permissionType) {
                case "WORKERS":
                    workerPermission = workerPermissionRepository.findById(permissionId).orElse(null);
                    break;
                case "STATUS_OWNERS":
                    statusOwnerPermission = statusOwnerPermissionRepository.findById(permissionId).orElse(null);
                    break;
                case "FIELD_OWNERS":
                    fieldOwnerPermission = fieldOwnerPermissionRepository.findById(permissionId).orElse(null);
                    break;
                case "CREATORS":
                    creatorPermission = creatorPermissionRepository.findById(permissionId).orElse(null);
                    break;
                case "EXECUTORS":
                    executorPermission = executorPermissionRepository.findById(permissionId).orElse(null);
                    break;
                case "EDITORS":
                case "VIEWERS":
                    fieldStatusPermission = fieldStatusPermissionRepository.findById(permissionId).orElse(null);
                    break;
                default:
                    throw new ApiException("Unknown permission type: " + permissionType);
            }
            
            if (workerPermission == null && statusOwnerPermission == null && fieldOwnerPermission == null &&
                creatorPermission == null && executorPermission == null && fieldStatusPermission == null) {
                throw new ApiException("Permission not found with ID: " + permissionId + " and type: " + permissionType);
            }
        }
        
        // Riutilizza la grant esistente se presente, altrimenti creane una nuova
        Grant grant = null;
        
        // Controlla se esiste già una grant per questa permission
        if (workerPermission != null && !workerPermission.getAssignedGrants().isEmpty()) {
            grant = workerPermission.getAssignedGrants().iterator().next();
        } else if (statusOwnerPermission != null && !statusOwnerPermission.getAssignedGrants().isEmpty()) {
            grant = statusOwnerPermission.getAssignedGrants().iterator().next();
        } else if (fieldOwnerPermission != null && !fieldOwnerPermission.getAssignedGrants().isEmpty()) {
            grant = fieldOwnerPermission.getAssignedGrants().iterator().next();
        } else if (creatorPermission != null && !creatorPermission.getAssignedGrants().isEmpty()) {
            grant = creatorPermission.getAssignedGrants().iterator().next();
        } else if (executorPermission != null && !executorPermission.getAssignedGrants().isEmpty()) {
            grant = executorPermission.getAssignedGrants().iterator().next();
        } else if (fieldStatusPermission != null && !fieldStatusPermission.getAssignedGrants().isEmpty()) {
            grant = fieldStatusPermission.getAssignedGrants().iterator().next();
        }
        
        // Se non esiste, creane una nuova
        if (grant == null) {
            grant = new Grant();
        } else {
            // Se esiste, pulisci le collezioni esistenti per riempirle con i nuovi dati
            grant.getUsers().clear();
            grant.getGroups().clear();
            grant.getNegatedUsers().clear();
            grant.getNegatedGroups().clear();
        }
        
        // Popola gli utenti assegnati
        if (grantData.containsKey("users") && grantData.get("users") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> usersData = (List<Map<String, Object>>) grantData.get("users");
            for (Map<String, Object> userData : usersData) {
                if (userData != null && userData.containsKey("id")) {
                    Long userId = ((Number) userData.get("id")).longValue();
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null) {
                        grant.getUsers().add(user);
                    }
                }
            }
        }
        
        // Popola i gruppi assegnati
        if (grantData.containsKey("groups") && grantData.get("groups") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groupsData = (List<Map<String, Object>>) grantData.get("groups");
            for (Map<String, Object> groupData : groupsData) {
                if (groupData != null && groupData.containsKey("id")) {
                    Long groupId = ((Number) groupData.get("id")).longValue();
                    Group group = groupRepository.findById(groupId).orElse(null);
                    if (group != null) {
                        grant.getGroups().add(group);
                    }
                }
            }
        }
        
        // Popola gli utenti negati
        if (grantData.containsKey("deniedUsers") && grantData.get("deniedUsers") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> deniedUsersData = (List<Map<String, Object>>) grantData.get("deniedUsers");
            for (Map<String, Object> userData : deniedUsersData) {
                if (userData != null && userData.containsKey("id")) {
                    Long userId = ((Number) userData.get("id")).longValue();
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null) {
                        grant.getNegatedUsers().add(user);
                    }
                }
            }
        }
        
        // Popola i gruppi negati
        if (grantData.containsKey("deniedGroups") && grantData.get("deniedGroups") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> deniedGroupsData = (List<Map<String, Object>>) grantData.get("deniedGroups");
            for (Map<String, Object> groupData : deniedGroupsData) {
                if (groupData != null && groupData.containsKey("id")) {
                    Long groupId = ((Number) groupData.get("id")).longValue();
                    Group group = groupRepository.findById(groupId).orElse(null);
                    if (group != null) {
                        grant.getNegatedGroups().add(group);
                    }
                }
            }
        }
        
        // Salva la grant (nuova o aggiornata)
        grant = grantRepository.save(grant);
        entityManager.flush(); // Forza la scrittura delle relazioni ManyToMany
        
        // Aggiungi la grant alla permission SOLO se non era già presente
        if (workerPermission != null) {
            if (!workerPermission.getAssignedGrants().contains(grant)) {
                workerPermission.getAssignedGrants().add(grant);
            }
            workerPermissionRepository.save(workerPermission);
            entityManager.flush();
        } else if (statusOwnerPermission != null) {
            if (!statusOwnerPermission.getAssignedGrants().contains(grant)) {
                statusOwnerPermission.getAssignedGrants().add(grant);
            }
            statusOwnerPermissionRepository.save(statusOwnerPermission);
            entityManager.flush();
        } else if (fieldOwnerPermission != null) {
            if (!fieldOwnerPermission.getAssignedGrants().contains(grant)) {
                fieldOwnerPermission.getAssignedGrants().add(grant);
            }
            fieldOwnerPermissionRepository.save(fieldOwnerPermission);
            entityManager.flush();
        } else if (creatorPermission != null) {
            if (!creatorPermission.getAssignedGrants().contains(grant)) {
                creatorPermission.getAssignedGrants().add(grant);
            }
            creatorPermissionRepository.save(creatorPermission);
            entityManager.flush();
        } else if (executorPermission != null) {
            if (!executorPermission.getAssignedGrants().contains(grant)) {
                executorPermission.getAssignedGrants().add(grant);
            }
            executorPermissionRepository.save(executorPermission);
            entityManager.flush();
        } else if (fieldStatusPermission != null) {
            if (!fieldStatusPermission.getAssignedGrants().contains(grant)) {
                fieldStatusPermission.getAssignedGrants().add(grant);
            }
            fieldStatusPermissionRepository.save(fieldStatusPermission);
            entityManager.flush();
        }
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
}

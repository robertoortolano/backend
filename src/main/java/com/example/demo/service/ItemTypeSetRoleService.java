package com.example.demo.service;

import com.example.demo.dto.ItemTypeSetRoleDTO;
import com.example.demo.dto.ItemTypeSetRoleCreateDTO;
import com.example.demo.dto.ItemTypeSetRoleGrantCreateDto;
import com.example.demo.entity.*;
import com.example.demo.enums.ItemTypeSetRoleType;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class ItemTypeSetRoleService {
    
    @Autowired
    private ItemTypeSetRoleRepository itemTypeSetRoleRepository;
    
    @Autowired
    private ItemTypeSetRepository itemTypeSetRepository;
    
    
    @Autowired
    private GrantRepository grantRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private GroupRepository groupRepository;
    
    @Autowired
    private ItemTypeConfigurationRepository itemTypeConfigurationRepository;
    
    @Autowired
    private ItemTypeRepository itemTypeRepository;
    
    @Autowired
    private WorkflowRepository workflowRepository;
    
    @Autowired
    private FieldConfigurationRepository fieldConfigurationRepository;
    
    @Autowired
    private WorkflowStatusRepository workflowStatusRepository;
    
    @Autowired
    private TransitionRepository transitionRepository;
    
    @Autowired
    private DtoMapperFacade dtoMapper;
    
    /**
     * Crea automaticamente tutti i ruoli necessari per un ItemTypeSet
     */
    public void createRolesForItemTypeSet(Long itemTypeSetId, Tenant tenant) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findById(itemTypeSetId)
                .orElseThrow(() -> new ApiException("ItemTypeSet not found"));
        
        // 1. Creare ruoli WORKER per ogni ItemType
        createWorkerRoles(itemTypeSet, tenant);
        
        // 2. Creare ruoli OWNER per ogni WorkflowStatus
        createOwnerRoles(itemTypeSet, tenant);
        
        // 3. Creare ruoli FIELD_EDITOR per ogni FieldConfiguration
        createFieldOwnerRoles(itemTypeSet, tenant);
        
        // 4. Creare ruoli CREATOR per ogni Workflow
        createCreatorRoles(itemTypeSet, tenant);
        
        // 5. Creare ruoli EXECUTOR per ogni Transition
        createExecutorRoles(itemTypeSet, tenant);
        
        // 6. Creare ruoli EDITOR e VIEWER per ogni coppia (FieldConfiguration, WorkflowStatus) in ItemTypeConfiguration
        createEditorAndViewerRolesForPairs(itemTypeSet, tenant);
    }
    
    private void createWorkerRoles(ItemTypeSet itemTypeSet, Tenant tenant) {
        Set<ItemTypeConfiguration> configurations = itemTypeSet.getItemTypeConfigurations();
        
        for (ItemTypeConfiguration config : configurations) {
            if (config.getItemType() != null) {
                ItemTypeSetRole role = ItemTypeSetRole.builder()
                        .roleType(ItemTypeSetRoleType.WORKERS)
                        .name("Worker for " + config.getItemType().getName())
                        .description("Worker role for ItemType: " + config.getItemType().getName())
                        .itemTypeSet(itemTypeSet)
                        .relatedEntityType("ItemType")
                        .relatedEntityId(config.getItemType().getId())
                        .tenant(tenant)
                        .build();
                
                itemTypeSetRoleRepository.save(role);
            }
        }
    }
    
    private void createOwnerRoles(ItemTypeSet itemTypeSet, Tenant tenant) {
        Set<ItemTypeConfiguration> configurations = itemTypeSet.getItemTypeConfigurations();
        
        for (ItemTypeConfiguration config : configurations) {
            if (config.getWorkflow() != null) {
                Set<WorkflowStatus> statuses = config.getWorkflow().getStatuses();
                for (WorkflowStatus status : statuses) {
                    ItemTypeSetRole role = ItemTypeSetRole.builder()
                            .roleType(ItemTypeSetRoleType.STATUS_OWNERS)
                            .name("Owner for " + status.getStatus().getName() + " in " + config.getWorkflow().getName())
                            .description("Owner role for WorkflowStatus: " + status.getStatus().getName())
                            .itemTypeSet(itemTypeSet)
                            .relatedEntityType("WorkflowStatus")
                            .relatedEntityId(status.getId())
                            .tenant(tenant)
                            .build();
                    
                    itemTypeSetRoleRepository.save(role);
                }
            }
        }
    }
    
    private void createFieldOwnerRoles(ItemTypeSet itemTypeSet, Tenant tenant) {
        Set<ItemTypeConfiguration> configurations = itemTypeSet.getItemTypeConfigurations();
        
        for (ItemTypeConfiguration config : configurations) {
            if (config.getFieldSet() != null) {
                List<FieldSetEntry> entries = config.getFieldSet().getFieldSetEntries();
                for (FieldSetEntry entry : entries) {
                    FieldConfiguration fieldConfig = entry.getFieldConfiguration();
                    
                    // Verifica se il ruolo esiste già
                    Optional<ItemTypeSetRole> existingRoleOpt = itemTypeSetRoleRepository
                            .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                                    itemTypeSet.getId(),
                                    "FieldConfiguration",
                                    fieldConfig.getId(),
                                    ItemTypeSetRoleType.FIELD_OWNERS,
                                    tenant.getId()
                            );
                    
                    if (existingRoleOpt.isPresent()) {
                        // IMPORTANTE: Se il ruolo esiste già (ad esempio quando si riaggiunge una FieldConfiguration),
                        // rimuovi la grant per evitare che venga preservata. I ruoli nelle permission vengono gestiti
                        // separatamente, ma le grant sugli ItemTypeSetRole devono essere rimosse.
                        ItemTypeSetRole existingRole = existingRoleOpt.get();
                        if (existingRole.getGrant() != null) {
                            existingRole.setGrant(null);
                            itemTypeSetRoleRepository.save(existingRole);
                        }
                    } else {
                        // Crea nuovo ruolo solo se non esiste
                        ItemTypeSetRole fieldOwnerRole = ItemTypeSetRole.builder()
                                .roleType(ItemTypeSetRoleType.FIELD_OWNERS)
                                .name("Field Owner for " + fieldConfig.getName())
                                .description("Field Owner role for FieldConfiguration: " + fieldConfig.getName())
                                .itemTypeSet(itemTypeSet)
                                .relatedEntityType("FieldConfiguration")
                                .relatedEntityId(fieldConfig.getId())
                                .tenant(tenant)
                                .build();
                        
                        itemTypeSetRoleRepository.save(fieldOwnerRole);
                    }
                }
            }
        }
    }
    
    private void createEditorAndViewerRolesForPairs(ItemTypeSet itemTypeSet, Tenant tenant) {
        Set<ItemTypeConfiguration> configurations = itemTypeSet.getItemTypeConfigurations();
        
        for (ItemTypeConfiguration config : configurations) {
            if (config.getFieldSet() != null && config.getWorkflow() != null) {
                List<FieldSetEntry> entries = config.getFieldSet().getFieldSetEntries();
                Set<WorkflowStatus> statuses = config.getWorkflow().getStatuses();
                
                // Creare ruoli EDITOR e VIEWER per ogni coppia (FieldConfiguration, WorkflowStatus)
                for (FieldSetEntry entry : entries) {
                    FieldConfiguration fieldConfig = entry.getFieldConfiguration();
                    
                    for (WorkflowStatus status : statuses) {
                        // Verifica se il ruolo EDITOR esiste già (verifica anche WorkflowStatus)
                        Optional<ItemTypeSetRole> existingEditorRoleOpt = itemTypeSetRoleRepository
                                .findByItemTypeSetIdAndRoleTypeAndTenantId(itemTypeSet.getId(), ItemTypeSetRoleType.EDITORS, tenant.getId())
                                .stream()
                                .filter(role -> role.getRelatedEntityId() != null && role.getRelatedEntityId().equals(config.getId()))
                                .filter(role -> role.getSecondaryEntityId() != null && role.getSecondaryEntityId().equals(fieldConfig.getId()))
                                .filter(role -> role.getTertiaryEntityId() != null && role.getTertiaryEntityId().equals(status.getId()))
                                .findFirst();
                        
                        if (existingEditorRoleOpt.isPresent()) {
                            // IMPORTANTE: Se il ruolo esiste già, rimuovi la grant per evitare che venga preservata
                            ItemTypeSetRole existingEditorRole = existingEditorRoleOpt.get();
                            if (existingEditorRole.getGrant() != null) {
                                existingEditorRole.setGrant(null);
                                itemTypeSetRoleRepository.save(existingEditorRole);
                            }
                        } else {
                            // Crea nuovo ruolo EDITOR solo se non esiste
                            ItemTypeSetRole editorRole = ItemTypeSetRole.builder()
                                    .roleType(ItemTypeSetRoleType.EDITORS)
                                    .name("Editor for " + fieldConfig.getName() + " in " + status.getStatus().getName())
                                    .description("Editor role for FieldConfiguration " + fieldConfig.getName() + " in WorkflowStatus " + status.getStatus().getName())
                                    .itemTypeSet(itemTypeSet)
                                    .relatedEntityType("ItemTypeConfiguration")
                                    .relatedEntityId(config.getId())
                                    .secondaryEntityType("FieldConfiguration")
                                    .secondaryEntityId(fieldConfig.getId())
                                    .tertiaryEntityType("WorkflowStatus")
                                    .tertiaryEntityId(status.getId())
                                    .tenant(tenant)
                                    .build();
                            
                            itemTypeSetRoleRepository.save(editorRole);
                        }
                        
                        // Verifica se il ruolo VIEWER esiste già (verifica anche WorkflowStatus)
                        Optional<ItemTypeSetRole> existingViewerRoleOpt = itemTypeSetRoleRepository
                                .findByItemTypeSetIdAndRoleTypeAndTenantId(itemTypeSet.getId(), ItemTypeSetRoleType.VIEWERS, tenant.getId())
                                .stream()
                                .filter(role -> role.getRelatedEntityId() != null && role.getRelatedEntityId().equals(config.getId()))
                                .filter(role -> role.getSecondaryEntityId() != null && role.getSecondaryEntityId().equals(fieldConfig.getId()))
                                .filter(role -> role.getTertiaryEntityId() != null && role.getTertiaryEntityId().equals(status.getId()))
                                .findFirst();
                        
                        if (existingViewerRoleOpt.isPresent()) {
                            // IMPORTANTE: Se il ruolo esiste già, rimuovi la grant per evitare che venga preservata
                            ItemTypeSetRole existingViewerRole = existingViewerRoleOpt.get();
                            if (existingViewerRole.getGrant() != null) {
                                existingViewerRole.setGrant(null);
                                itemTypeSetRoleRepository.save(existingViewerRole);
                            }
                        } else {
                            // Crea nuovo ruolo VIEWER solo se non esiste
                            ItemTypeSetRole viewerRole = ItemTypeSetRole.builder()
                                    .roleType(ItemTypeSetRoleType.VIEWERS)
                                    .name("Viewer for " + fieldConfig.getName() + " in " + status.getStatus().getName())
                                    .description("Viewer role for FieldConfiguration " + fieldConfig.getName() + " in WorkflowStatus " + status.getStatus().getName())
                                    .itemTypeSet(itemTypeSet)
                                    .relatedEntityType("ItemTypeConfiguration")
                                    .relatedEntityId(config.getId())
                                    .secondaryEntityType("FieldConfiguration")
                                    .secondaryEntityId(fieldConfig.getId())
                                    .tertiaryEntityType("WorkflowStatus")
                                    .tertiaryEntityId(status.getId())
                                    .tenant(tenant)
                                    .build();
                            
                            itemTypeSetRoleRepository.save(viewerRole);
                        }
                    }
                }
            }
        }
    }
    
    private void createCreatorRoles(ItemTypeSet itemTypeSet, Tenant tenant) {
        Set<ItemTypeConfiguration> configurations = itemTypeSet.getItemTypeConfigurations();
        
        for (ItemTypeConfiguration config : configurations) {
            if (config.getWorkflow() != null) {
                ItemTypeSetRole role = ItemTypeSetRole.builder()
                        .roleType(ItemTypeSetRoleType.CREATORS)
                        .name("Creator for " + config.getWorkflow().getName())
                        .description("Creator role for Workflow: " + config.getWorkflow().getName())
                        .itemTypeSet(itemTypeSet)
                        .relatedEntityType("Workflow")
                        .relatedEntityId(config.getWorkflow().getId())
                        .tenant(tenant)
                        .build();
                
                itemTypeSetRoleRepository.save(role);
            }
        }
    }
    
    private void createExecutorRoles(ItemTypeSet itemTypeSet, Tenant tenant) {
        Set<ItemTypeConfiguration> configurations = itemTypeSet.getItemTypeConfigurations();
        
        for (ItemTypeConfiguration config : configurations) {
            if (config.getWorkflow() != null) {
                Set<Transition> transitions = config.getWorkflow().getTransitions();
                for (Transition transition : transitions) {
                    ItemTypeSetRole role = ItemTypeSetRole.builder()
                            .roleType(ItemTypeSetRoleType.EXECUTORS)
                            .name("Executor for " + transition.getName())
                            .description("Executor role for Transition: " + transition.getName())
                            .itemTypeSet(itemTypeSet)
                            .relatedEntityType("Transition")
                            .relatedEntityId(transition.getId())
                            .tenant(tenant)
                            .build();
                    
                    itemTypeSetRoleRepository.save(role);
                }
            }
        }
    }
    
    
    
    /**
     * Ottiene tutti i ruoli per un ItemTypeSet
     */
    public List<ItemTypeSetRoleDTO> getRolesByItemTypeSet(Long itemTypeSetId, Tenant tenant) {
        List<ItemTypeSetRole> roles = itemTypeSetRoleRepository.findByItemTypeSetIdAndTenantId(itemTypeSetId, tenant.getId());
        return dtoMapper.toItemTypeSetRoleDTOs(roles);
    }
    
    /**
     * Ottiene i ruoli per tipo specifico
     */
    public List<ItemTypeSetRoleDTO> getRolesByType(Long itemTypeSetId, ItemTypeSetRoleType roleType, Tenant tenant) {
        List<ItemTypeSetRole> roles = itemTypeSetRoleRepository.findRolesByItemTypeSetAndType(itemTypeSetId, roleType, tenant.getId());
        return dtoMapper.toItemTypeSetRoleDTOs(roles);
    }
    
    /**
     * Assegna un Grant diretto a un ruolo specifico (usando un Grant esistente)
     */
    public ItemTypeSetRoleDTO assignGrantDirect(Long roleId, Long grantId, Tenant tenant) {
        ItemTypeSetRole role = itemTypeSetRoleRepository.findByIdAndTenant(roleId, tenant)
                .orElseThrow(() -> new ApiException("Role not found"));
        
        Grant grant = grantRepository.findByIdAndTenant(grantId, tenant)
                .orElseThrow(() -> new ApiException("Grant not found"));
        
        // Assegna il Grant senza rimuovere il RoleTemplate (ora sono compatibili)
        role.setGrant(grant);
        
        
        ItemTypeSetRole savedRole = itemTypeSetRoleRepository.save(role);
        return dtoMapper.toItemTypeSetRoleDTO(savedRole);
    }
    
    /**
     * Trova o crea un ItemTypeSetRole basandosi sulle informazioni fornite
     */
    private ItemTypeSetRole findOrCreateItemTypeSetRole(ItemTypeSetRoleGrantCreateDto dto, Tenant tenant) {
        // Prima prova a trovarlo per ID
        Optional<ItemTypeSetRole> roleOpt = itemTypeSetRoleRepository.findByIdAndTenant(dto.itemTypeSetRoleId(), tenant);
        if (roleOpt.isPresent()) {
            return roleOpt.get();
        }
        
        // Se non esiste e abbiamo le informazioni necessarie, crealo
        if (dto.itemTypeSetId() == null || dto.permissionType() == null) {
            throw new ApiException(String.format(
                "ItemTypeSetRole with id %d not found for tenant %d and insufficient information to create it. " +
                "itemTypeSetId and permissionType are required.",
                dto.itemTypeSetRoleId(), 
                tenant.getId()
            ));
        }
        
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByIdAndTenant(dto.itemTypeSetId(), tenant)
                .orElseThrow(() -> new ApiException("ItemTypeSet not found: " + dto.itemTypeSetId()));
        
        ItemTypeSetRole role;
        
        switch (dto.permissionType()) {
            case WORKERS:
                if (dto.itemTypeId() == null) {
                    throw new ApiException("itemTypeId is required for WORKERS permission type");
                }
                // Cerca se esiste già
                role = itemTypeSetRoleRepository
                    .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                        dto.itemTypeSetId(), "ItemType", dto.itemTypeId(), ItemTypeSetRoleType.WORKERS, tenant.getId()
                    )
                    .orElseGet(() -> {
                        ItemTypeSetRole newRole = ItemTypeSetRole.builder()
                                .roleType(ItemTypeSetRoleType.WORKERS)
                                .name("Worker for ItemType " + dto.itemTypeId())
                                .description("Worker role")
                                .itemTypeSet(itemTypeSet)
                                .relatedEntityType("ItemType")
                                .relatedEntityId(dto.itemTypeId())
                                .tenant(tenant)
                                .build();
                        return itemTypeSetRoleRepository.save(newRole);
                    });
                break;
                
            case STATUS_OWNERS:
                if (dto.workflowStatusId() == null) {
                    throw new ApiException("workflowStatusId is required for STATUS_OWNERS permission type");
                }
                role = itemTypeSetRoleRepository
                    .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                        dto.itemTypeSetId(), "WorkflowStatus", dto.workflowStatusId(), ItemTypeSetRoleType.STATUS_OWNERS, tenant.getId()
                    )
                    .orElseGet(() -> {
                        ItemTypeSetRole newRole = ItemTypeSetRole.builder()
                                .roleType(ItemTypeSetRoleType.STATUS_OWNERS)
                                .name("Status Owner for WorkflowStatus " + dto.workflowStatusId())
                                .description("Status Owner role")
                                .itemTypeSet(itemTypeSet)
                                .relatedEntityType("WorkflowStatus")
                                .relatedEntityId(dto.workflowStatusId())
                                .tenant(tenant)
                                .build();
                        return itemTypeSetRoleRepository.save(newRole);
                    });
                break;
                
            case FIELD_OWNERS:
                if (dto.fieldConfigurationId() == null) {
                    throw new ApiException("fieldConfigurationId is required for FIELD_OWNERS permission type");
                }
                role = itemTypeSetRoleRepository
                    .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                        dto.itemTypeSetId(), "FieldConfiguration", dto.fieldConfigurationId(), ItemTypeSetRoleType.FIELD_OWNERS, tenant.getId()
                    )
                    .orElseGet(() -> {
                        ItemTypeSetRole newRole = ItemTypeSetRole.builder()
                                .roleType(ItemTypeSetRoleType.FIELD_OWNERS)
                                .name("Field Owner for FieldConfiguration " + dto.fieldConfigurationId())
                                .description("Field Owner role")
                                .itemTypeSet(itemTypeSet)
                                .relatedEntityType("FieldConfiguration")
                                .relatedEntityId(dto.fieldConfigurationId())
                                .tenant(tenant)
                                .build();
                        return itemTypeSetRoleRepository.save(newRole);
                    });
                break;
                
            case CREATORS:
                if (dto.workflowId() == null) {
                    throw new ApiException("workflowId is required for CREATORS permission type");
                }
                role = itemTypeSetRoleRepository
                    .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                        dto.itemTypeSetId(), "Workflow", dto.workflowId(), ItemTypeSetRoleType.CREATORS, tenant.getId()
                    )
                    .orElseGet(() -> {
                        ItemTypeSetRole newRole = ItemTypeSetRole.builder()
                                .roleType(ItemTypeSetRoleType.CREATORS)
                                .name("Creator for Workflow " + dto.workflowId())
                                .description("Creator role")
                                .itemTypeSet(itemTypeSet)
                                .relatedEntityType("Workflow")
                                .relatedEntityId(dto.workflowId())
                                .tenant(tenant)
                                .build();
                        return itemTypeSetRoleRepository.save(newRole);
                    });
                break;
                
            case EXECUTORS:
                if (dto.transitionId() == null) {
                    throw new ApiException("transitionId is required for EXECUTORS permission type");
                }
                role = itemTypeSetRoleRepository
                    .findByItemTypeSetIdAndRelatedEntityTypeAndRelatedEntityIdAndRoleTypeAndTenantId(
                        dto.itemTypeSetId(), "Transition", dto.transitionId(), ItemTypeSetRoleType.EXECUTORS, tenant.getId()
                    )
                    .orElseGet(() -> {
                        ItemTypeSetRole newRole = ItemTypeSetRole.builder()
                                .roleType(ItemTypeSetRoleType.EXECUTORS)
                                .name("Executor for Transition " + dto.transitionId())
                                .description("Executor role")
                                .itemTypeSet(itemTypeSet)
                                .relatedEntityType("Transition")
                                .relatedEntityId(dto.transitionId())
                                .tenant(tenant)
                                .build();
                        return itemTypeSetRoleRepository.save(newRole);
                    });
                break;
                
            case EDITORS:
            case VIEWERS:
                // Per EDITORS e VIEWERS, dobbiamo trovare l'ItemTypeConfiguration che corrisponde
                if (dto.fieldConfigurationId() == null || dto.workflowStatusId() == null) {
                    throw new ApiException("fieldConfigurationId and workflowStatusId are required for " + dto.permissionType() + " permission type");
                }
                // Trovare ItemTypeConfiguration che contiene questo fieldConfiguration e workflowStatus
                Set<ItemTypeConfiguration> configs = itemTypeSet.getItemTypeConfigurations();
                final ItemTypeConfiguration matchingConfig = configs.stream()
                    .filter(config -> {
                        // Verifica se il FieldSet contiene questo FieldConfiguration
                        boolean hasField = config.getFieldSet() != null && 
                            config.getFieldSet().getFieldSetEntries().stream()
                                .anyMatch(e -> e.getFieldConfiguration().getId().equals(dto.fieldConfigurationId()));
                        // Verifica se il Workflow contiene questo WorkflowStatus
                        boolean hasStatus = config.getWorkflow() != null &&
                            config.getWorkflow().getStatuses().stream()
                                .anyMatch(s -> s.getId().equals(dto.workflowStatusId()));
                        return hasField && hasStatus;
                    })
                    .findFirst()
                    .orElseThrow(() -> new ApiException("No ItemTypeConfiguration found matching fieldConfigurationId " + 
                        dto.fieldConfigurationId() + " and workflowStatusId " + dto.workflowStatusId()));
                
                // Cerca ruolo esistente - per EDITORS/VIEWERS dobbiamo cercare manualmente perché
                // dobbiamo verificare anche il secondaryEntityId (FieldConfiguration) e tertiaryEntityId (WorkflowStatus)
                List<ItemTypeSetRole> existingRoles = itemTypeSetRoleRepository
                    .findRolesByItemTypeSetAndType(dto.itemTypeSetId(), dto.permissionType(), tenant.getId());
                
                Optional<ItemTypeSetRole> existingRole = existingRoles.stream()
                    .filter(r -> {
                        // Verifica che corrisponda all'ItemTypeConfiguration, al FieldConfiguration E al WorkflowStatus
                        return r.getRelatedEntityType() != null &&
                               r.getRelatedEntityType().equals("ItemTypeConfiguration") &&
                               r.getRelatedEntityId() != null &&
                               r.getRelatedEntityId().equals(matchingConfig.getId()) &&
                               r.getSecondaryEntityType() != null && 
                               r.getSecondaryEntityType().equals("FieldConfiguration") &&
                               r.getSecondaryEntityId() != null &&
                               r.getSecondaryEntityId().equals(dto.fieldConfigurationId()) &&
                               r.getTertiaryEntityType() != null &&
                               r.getTertiaryEntityType().equals("WorkflowStatus") &&
                               r.getTertiaryEntityId() != null &&
                               r.getTertiaryEntityId().equals(dto.workflowStatusId());
                    })
                    .findFirst();
                
                if (existingRole.isPresent()) {
                    role = existingRole.get();
                } else {
                    role = ItemTypeSetRole.builder()
                            .roleType(dto.permissionType())
                            .name(dto.permissionType().name() + " for FieldConfiguration " + dto.fieldConfigurationId() + " in WorkflowStatus " + dto.workflowStatusId())
                            .description(dto.permissionType().name() + " role")
                            .itemTypeSet(itemTypeSet)
                            .relatedEntityType("ItemTypeConfiguration")
                            .relatedEntityId(matchingConfig.getId())
                            .secondaryEntityType("FieldConfiguration")
                            .secondaryEntityId(dto.fieldConfigurationId())
                            .tertiaryEntityType("WorkflowStatus")
                            .tertiaryEntityId(dto.workflowStatusId())
                            .tenant(tenant)
                            .build();
                    role = itemTypeSetRoleRepository.save(role);
                }
                break;
                
            default:
                throw new ApiException("Unsupported permission type: " + dto.permissionType());
        }
        
        return role;
    }
    
    /**
     * Crea un Grant e lo assegna direttamente a un ItemTypeSetRole.
     * Il Grant viene creato al momento dell'assegnazione con gli utenti/gruppi specificati.
     */
    public ItemTypeSetRoleDTO createAndAssignGrant(ItemTypeSetRoleGrantCreateDto dto, Tenant tenant) {
        // Trova o crea l'ItemTypeSetRole
        ItemTypeSetRole role;
        try {
            role = findOrCreateItemTypeSetRole(dto, tenant);
        } catch (ApiException e) {
            // Se non possiamo crearlo, verifica se esiste per un altro tenant
            Optional<ItemTypeSetRole> roleById = itemTypeSetRoleRepository.findById(dto.itemTypeSetRoleId());
            if (roleById.isPresent()) {
                ItemTypeSetRole foundRole = roleById.get();
                throw new ApiException(String.format(
                    "ItemTypeSetRole with id %d exists but belongs to tenant %d (current tenant: %d). Role details: name=%s, roleType=%s, itemTypeSet=%d",
                    dto.itemTypeSetRoleId(), 
                    foundRole.getTenant().getId(), 
                    tenant.getId(),
                    foundRole.getName(),
                    foundRole.getRoleType(),
                    foundRole.getItemTypeSet().getId()
                ));
            } else {
                throw e;
            }
        }
        
        // Crea il nuovo Grant
        Grant grant = new Grant();
        // Il campo role del Grant è null quando assegnato direttamente a ItemTypeSetRole
        // Questo è supportato perché Grant.role è annotato come @ManyToOne(optional = true)
        grant.setRole(null);
        
        // Aggiungi utenti
        if (dto.userIds() != null && !dto.userIds().isEmpty()) {
            Set<User> users = new HashSet<>();
            for (Long userId : dto.userIds()) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ApiException("User not found: " + userId));
                // Nota: La verifica tenant per User è gestita tramite UserRole
                // Qui assumiamo che se l'utente viene passato, appartiene alla tenant
                users.add(user);
            }
            grant.setUsers(users);
        }
        
        // Aggiungi gruppi
        if (dto.groupIds() != null && !dto.groupIds().isEmpty()) {
            Set<Group> groups = new HashSet<>();
            for (Long groupId : dto.groupIds()) {
                Group group = groupRepository.findByIdAndTenant(groupId, tenant)
                        .orElseThrow(() -> new ApiException("Group not found: " + groupId));
                groups.add(group);
            }
            grant.setGroups(groups);
        }
        
        // Aggiungi utenti negati
        if (dto.negatedUserIds() != null && !dto.negatedUserIds().isEmpty()) {
            Set<User> negatedUsers = new HashSet<>();
            for (Long userId : dto.negatedUserIds()) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ApiException("Negated user not found: " + userId));
                // Nota: La verifica tenant per User è gestita tramite UserRole
                negatedUsers.add(user);
            }
            grant.setNegatedUsers(negatedUsers);
        }
        
        // Aggiungi gruppi negati
        if (dto.negatedGroupIds() != null && !dto.negatedGroupIds().isEmpty()) {
            Set<Group> negatedGroups = new HashSet<>();
            for (Long groupId : dto.negatedGroupIds()) {
                Group group = groupRepository.findByIdAndTenant(groupId, tenant)
                        .orElseThrow(() -> new ApiException("Negated group not found: " + groupId));
                negatedGroups.add(group);
            }
            grant.setNegatedGroups(negatedGroups);
        }
        
        // Salva il Grant
        Grant savedGrant = grantRepository.save(grant);
        
        // Assegna il Grant senza rimuovere il RoleTemplate (ora sono compatibili)
        role.setGrant(savedGrant);
        
        ItemTypeSetRole savedRole = itemTypeSetRoleRepository.save(role);
        return dtoMapper.toItemTypeSetRoleDTO(savedRole);
    }
    
    /**
     * Recupera i dettagli di un Grant assegnato a un ItemTypeSetRole
     */
    public com.example.demo.dto.GrantDetailsDto getGrantDetails(Long itemTypeSetRoleId, Tenant tenant) {
        ItemTypeSetRole role = itemTypeSetRoleRepository.findByIdAndTenant(itemTypeSetRoleId, tenant)
                .orElseThrow(() -> new ApiException("ItemTypeSetRole not found"));
        
        if (role.getGrant() == null) {
            throw new ApiException("No Grant assigned to this ItemTypeSetRole");
        }
        
        Grant grant = grantRepository.findByIdWithCollections(role.getGrant().getId())
                .orElseThrow(() -> new ApiException("Grant not found"));
        
        return new com.example.demo.dto.GrantDetailsDto(
            grant.getId(),
            grant.getUsers().stream().map(dtoMapper::toUserSimpleDto).toList(),
            grant.getGroups().stream().map(dtoMapper::toGroupSimpleDto).toList(),
            grant.getNegatedUsers().stream().map(dtoMapper::toUserSimpleDto).toList(),
            grant.getNegatedGroups().stream().map(dtoMapper::toGroupSimpleDto).toList()
        );
    }
    
    /**
     * Aggiorna un Grant esistente assegnato a un ItemTypeSetRole
     */
    public ItemTypeSetRoleDTO updateGrant(ItemTypeSetRoleGrantCreateDto dto, Tenant tenant) {
        ItemTypeSetRole role = itemTypeSetRoleRepository.findByIdAndTenant(dto.itemTypeSetRoleId(), tenant)
                .orElseThrow(() -> new ApiException("ItemTypeSetRole not found"));
        
        if (role.getGrant() == null) {
            throw new ApiException("No Grant assigned to this ItemTypeSetRole");
        }
        
        Grant grant = grantRepository.findByIdWithCollections(role.getGrant().getId())
                .orElseThrow(() -> new ApiException("Grant not found"));
        
        // Aggiorna utenti
        grant.getUsers().clear();
        if (dto.userIds() != null && !dto.userIds().isEmpty()) {
            for (Long userId : dto.userIds()) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ApiException("User not found: " + userId));
                grant.getUsers().add(user);
            }
        }
        
        // Aggiorna gruppi
        grant.getGroups().clear();
        if (dto.groupIds() != null && !dto.groupIds().isEmpty()) {
            for (Long groupId : dto.groupIds()) {
                Group group = groupRepository.findByIdAndTenant(groupId, tenant)
                        .orElseThrow(() -> new ApiException("Group not found: " + groupId));
                grant.getGroups().add(group);
            }
        }
        
        // Aggiorna utenti negati
        grant.getNegatedUsers().clear();
        if (dto.negatedUserIds() != null && !dto.negatedUserIds().isEmpty()) {
            for (Long userId : dto.negatedUserIds()) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ApiException("Negated user not found: " + userId));
                grant.getNegatedUsers().add(user);
            }
        }
        
        // Aggiorna gruppi negati
        grant.getNegatedGroups().clear();
        if (dto.negatedGroupIds() != null && !dto.negatedGroupIds().isEmpty()) {
            for (Long groupId : dto.negatedGroupIds()) {
                Group group = groupRepository.findByIdAndTenant(groupId, tenant)
                        .orElseThrow(() -> new ApiException("Negated group not found: " + groupId));
                grant.getNegatedGroups().add(group);
            }
        }
        
        grantRepository.save(grant);
        
        ItemTypeSetRole savedRole = itemTypeSetRoleRepository.save(role);
        return dtoMapper.toItemTypeSetRoleDTO(savedRole);
    }
    
    /**
     * Assegna un Role template a un ruolo specifico
     */
    public ItemTypeSetRoleDTO assignRoleTemplate(Long roleId, Long roleTemplateId, Tenant tenant) {
        ItemTypeSetRole role = itemTypeSetRoleRepository.findByIdAndTenant(roleId, tenant)
                .orElseThrow(() -> new ApiException("Role not found"));
        
        Role roleTemplate = roleRepository.findByIdAndTenant(roleTemplateId, tenant)
                .orElseThrow(() -> new ApiException("Role template not found"));
        
        // Assegna il RoleTemplate senza rimuovere il Grant (ora sono compatibili)
        role.setRoleTemplate(roleTemplate);
        
        
        ItemTypeSetRole savedRole = itemTypeSetRoleRepository.save(role);
        return dtoMapper.toItemTypeSetRoleDTO(savedRole);
    }
    
    /**
     * Rimuove l'assegnazione (Grant o Role) da un ruolo specifico
     */
    public void removeAssignment(Long roleId, Tenant tenant) {
        ItemTypeSetRole role = itemTypeSetRoleRepository.findByIdAndTenant(roleId, tenant)
                .orElseThrow(() -> new ApiException("Role not found"));
        
        // Rimuovi tutte le assegnazioni
        role.setGrant(null);
        role.setRoleTemplate(null);
        
        
        itemTypeSetRoleRepository.save(role);
    }
    
    /**
     * Elimina tutti i ruoli per un ItemTypeSet
     */
    public void deleteAllRolesForItemTypeSet(Long itemTypeSetId, Tenant tenant) {
        // Elimina tutti i ruoli
        itemTypeSetRoleRepository.deleteByItemTypeSetIdAndTenantId(itemTypeSetId, tenant.getId());
    }
    
    /**
     * Crea un ruolo manualmente
     */
    public ItemTypeSetRoleDTO createRole(ItemTypeSetRoleCreateDTO createDTO, Tenant tenant) {
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findById(createDTO.getItemTypeSetId())
                .orElseThrow(() -> new ApiException("ItemTypeSet not found"));
        
        ItemTypeSetRole role = ItemTypeSetRole.builder()
                .roleType(createDTO.getRoleType())
                .name(createDTO.getName())
                .description(createDTO.getDescription())
                .itemTypeSet(itemTypeSet)
                .relatedEntityType(createDTO.getRelatedEntityType())
                .relatedEntityId(createDTO.getRelatedEntityId())
                .secondaryEntityType(createDTO.getSecondaryEntityType())
                .secondaryEntityId(createDTO.getSecondaryEntityId())
                .tenant(tenant)
                .build();
        
        ItemTypeSetRole savedRole = itemTypeSetRoleRepository.save(role);
        return dtoMapper.toItemTypeSetRoleDTO(savedRole);
    }
}

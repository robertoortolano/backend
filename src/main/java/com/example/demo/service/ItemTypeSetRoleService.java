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
                    
                    // Ruolo FIELD_EDITOR
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
                        // Ruolo EDITOR per la coppia
                        ItemTypeSetRole editorRole = ItemTypeSetRole.builder()
                                .roleType(ItemTypeSetRoleType.EDITORS)
                                .name("Editor for " + fieldConfig.getName() + " in " + status.getStatus().getName())
                                .description("Editor role for FieldConfiguration " + fieldConfig.getName() + " in WorkflowStatus " + status.getStatus().getName())
                                .itemTypeSet(itemTypeSet)
                                .relatedEntityType("ItemTypeConfiguration")
                                .relatedEntityId(config.getId())
                                .secondaryEntityType("FieldConfiguration")
                                .secondaryEntityId(fieldConfig.getId())
                                .tenant(tenant)
                                .build();
                        
                        // Ruolo VIEWER per la coppia
                        ItemTypeSetRole viewerRole = ItemTypeSetRole.builder()
                                .roleType(ItemTypeSetRoleType.VIEWERS)
                                .name("Viewer for " + fieldConfig.getName() + " in " + status.getStatus().getName())
                                .description("Viewer role for FieldConfiguration " + fieldConfig.getName() + " in WorkflowStatus " + status.getStatus().getName())
                                .itemTypeSet(itemTypeSet)
                                .relatedEntityType("ItemTypeConfiguration")
                                .relatedEntityId(config.getId())
                                .secondaryEntityType("FieldConfiguration")
                                .secondaryEntityId(fieldConfig.getId())
                                .tenant(tenant)
                                .build();
                        
                        itemTypeSetRoleRepository.save(editorRole);
                        itemTypeSetRoleRepository.save(viewerRole);
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
     * Crea un Grant e lo assegna direttamente a un ItemTypeSetRole.
     * Il Grant viene creato al momento dell'assegnazione con gli utenti/gruppi specificati.
     */
    public ItemTypeSetRoleDTO createAndAssignGrant(ItemTypeSetRoleGrantCreateDto dto, Tenant tenant) {
        // Verifica che l'ItemTypeSetRole esista
        Optional<ItemTypeSetRole> roleOpt = itemTypeSetRoleRepository.findByIdAndTenant(dto.itemTypeSetRoleId(), tenant);
        
        if (roleOpt.isEmpty()) {
            // Log per debugging
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
                throw new ApiException(String.format(
                    "ItemTypeSetRole with id %d not found for tenant %d. " +
                    "This might happen if the permissions were not fully loaded. " +
                    "Please reload the permissions page to ensure all ItemTypeSetRoles are created.",
                    dto.itemTypeSetRoleId(), 
                    tenant.getId()
                ));
            }
        }
        
        ItemTypeSetRole role = roleOpt.get();
        
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

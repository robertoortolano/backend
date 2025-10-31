package com.example.demo.service;

import com.example.demo.dto.ItemTypeSetRoleDTO;
import com.example.demo.dto.ItemTypeSetRoleCreateDTO;
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
     * Assegna un Grant diretto a un ruolo specifico
     */
    public ItemTypeSetRoleDTO assignGrantDirect(Long roleId, Long grantId, Tenant tenant) {
        ItemTypeSetRole role = itemTypeSetRoleRepository.findByIdAndTenant(roleId, tenant)
                .orElseThrow(() -> new ApiException("Role not found"));
        
        Grant grant = grantRepository.findByIdAndTenant(grantId, tenant)
                .orElseThrow(() -> new ApiException("Grant not found"));
        
        // Rimuovi eventuali assegnazioni precedenti
        role.setGrant(grant);
        role.setRoleTemplate(null);
        
        
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
        
        // Rimuovi eventuali assegnazioni precedenti
        role.setRoleTemplate(roleTemplate);
        role.setGrant(null);
        
        
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

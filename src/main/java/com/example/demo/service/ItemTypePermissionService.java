package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ItemTypePermissionService {

    private final WorkerPermissionRepository workerPermissionRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final CreatorPermissionRepository creatorPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final ItemTypeConfigurationRepository itemTypeConfigurationRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final TransitionRepository transitionRepository;
    private final FieldLookup fieldLookup;

    /**
     * Crea automaticamente tutte le permissions per un ItemTypeConfiguration
     */
    public void createPermissionsForItemTypeConfiguration(ItemTypeConfiguration itemTypeConfiguration) {
        ItemTypeConfiguration config = itemTypeConfigurationRepository.findById(itemTypeConfiguration.getId())
                .orElseThrow(() -> new ApiException("ItemTypeConfiguration not found with id: " + itemTypeConfiguration.getId()));
        
        // 1. Workers - uno per ItemType
        createWorkersPermission(config);

        // 2. StatusOwner - uno per ogni WorkflowStatus
        createStatusOwnerPermissions(config);

        // 3. FieldOwners - uno per ogni FieldConfiguration
        createFieldOwnerPermissions(config);

        // 4. Creators - uno per ItemType
        createCreatorPermission(config);

        // 5. Executors - uno per ogni Transition del Workflow
        createExecutorPermissions(config);

        // 6. Editors e Viewers - uno per ogni coppia (FieldConfiguration, WorkflowStatus)
        createFieldStatusPermissions(config);
    }

    private void createWorkersPermission(ItemTypeConfiguration itemTypeConfiguration) {
        // Crea una WorkerPermission per ogni ItemTypeConfiguration
        if (!workerPermissionRepository.findAllByItemTypeConfiguration(itemTypeConfiguration).isEmpty()) {
            return; // Già esistente
        }
        
        WorkerPermission workerPermission = new WorkerPermission();
        workerPermission.setItemTypeConfiguration(itemTypeConfiguration);
        workerPermission.setAssignedRoles(new HashSet<>());
        
        workerPermissionRepository.save(workerPermission);
    }

    private void createStatusOwnerPermissions(ItemTypeConfiguration itemTypeConfiguration) {
        // Crea una permission per ogni WorkflowStatus del workflow
        if (itemTypeConfiguration.getWorkflow() == null) {
            return;
        }
        
        // Carica ESPLICITAMENTE tutti i WorkflowStatus per questo workflow dal database
        // Questo bypassa il problema del lazy loading
        var workflowStatuses = workflowStatusRepository.findAllByWorkflowId(
                itemTypeConfiguration.getWorkflow().getId());
        
        for (WorkflowStatus workflowStatus : workflowStatuses) {
            if (!statusOwnerPermissionRepository.existsByItemTypeConfigurationIdAndWorkflowStatusId(
                    itemTypeConfiguration.getId(), workflowStatus.getId())) {
                
                StatusOwnerPermission permission = new StatusOwnerPermission();
                permission.setItemTypeConfiguration(itemTypeConfiguration);
                permission.setWorkflowStatus(workflowStatus);
                permission.setAssignedRoles(new HashSet<>());
                
                statusOwnerPermissionRepository.save(permission);
            }
        }
    }

    private void createFieldOwnerPermissions(ItemTypeConfiguration itemTypeConfiguration) {
        // Crea una permission per ogni Field del FieldSet (non FieldConfiguration!)
        if (itemTypeConfiguration.getFieldSet() == null || itemTypeConfiguration.getFieldSet().getFieldSetEntries().isEmpty()) {
            return;
        }
        
        // Raccogli i Field unici (per evitare duplicati se ci sono più FieldConfiguration per lo stesso Field)
        Set<Long> fieldIds = itemTypeConfiguration.getFieldSet().getFieldSetEntries().stream()
                .map(entry -> entry.getFieldConfiguration().getField().getId())
                .collect(java.util.stream.Collectors.toSet());
        
        for (Long fieldId : fieldIds) {
            if (!fieldOwnerPermissionRepository.existsByItemTypeConfigurationIdAndFieldId(
                    itemTypeConfiguration.getId(), fieldId)) {
                
                Field field = fieldLookup.getById(fieldId, itemTypeConfiguration.getTenant());

                FieldOwnerPermission permission = new FieldOwnerPermission();
                permission.setItemTypeConfiguration(itemTypeConfiguration);
                permission.setField(field);
                permission.setAssignedRoles(new HashSet<>());
                
                fieldOwnerPermissionRepository.save(permission);
            }
        }
    }

    private void createCreatorPermission(ItemTypeConfiguration itemTypeConfiguration) {
        if (!creatorPermissionRepository.existsByItemTypeConfigurationId(itemTypeConfiguration.getId())) {
            CreatorPermission permission = new CreatorPermission();
            permission.setItemTypeConfiguration(itemTypeConfiguration);
            permission.setAssignedRoles(new HashSet<>());
            
            creatorPermissionRepository.save(permission);
        }
    }

    private void createExecutorPermissions(ItemTypeConfiguration itemTypeConfiguration) {
        // Crea una permission per ogni Transition del Workflow
        if (itemTypeConfiguration.getWorkflow() == null) {
            return;
        }
        
        // Carica ESPLICITAMENTE tutte le Transitions per questo workflow dal repository
        var transitions = transitionRepository.findByWorkflow(itemTypeConfiguration.getWorkflow());
        
        for (Transition transition : transitions) {
            if (!executorPermissionRepository.existsByItemTypeConfigurationIdAndTransitionId(
                    itemTypeConfiguration.getId(), transition.getId())) {
                
                ExecutorPermission permission = new ExecutorPermission();
                permission.setItemTypeConfiguration(itemTypeConfiguration);
                permission.setTransition(transition);
                permission.setAssignedRoles(new HashSet<>());
                
                executorPermissionRepository.save(permission);
            }
        }
    }

    private void createFieldStatusPermissions(ItemTypeConfiguration itemTypeConfiguration) {
        // Crea una permission per ogni coppia (Field, WorkflowStatus) - non FieldConfiguration!
        if (itemTypeConfiguration.getFieldSet() == null || itemTypeConfiguration.getFieldSet().getFieldSetEntries().isEmpty()) {
            return;
        }
        
        if (itemTypeConfiguration.getWorkflow() == null) {
            return;
        }
        
        // Carica ESPLICITAMENTE i WorkflowStatus per questo workflow
        var workflowStatuses = workflowStatusRepository.findAllByWorkflowId(
                itemTypeConfiguration.getWorkflow().getId());
        
        // Raccogli i Field unici (per evitare duplicati se ci sono più FieldConfiguration per lo stesso Field)
        Set<Long> fieldIds = itemTypeConfiguration.getFieldSet().getFieldSetEntries().stream()
                .map(entry -> entry.getFieldConfiguration().getField().getId())
                .collect(java.util.stream.Collectors.toSet());
        
        for (Long fieldId : fieldIds) {
            Field field = fieldLookup.getById(fieldId, itemTypeConfiguration.getTenant());
            
            for (WorkflowStatus workflowStatus : workflowStatuses) {
                // Editor permission
                if (!fieldStatusPermissionRepository.existsByItemTypeConfigurationIdAndFieldIdAndWorkflowStatusIdAndPermissionType(
                        itemTypeConfiguration.getId(), fieldId, workflowStatus.getId(), 
                        FieldStatusPermission.PermissionType.EDITORS)) {
                    
                    FieldStatusPermission editorPerm = new FieldStatusPermission();
                    editorPerm.setItemTypeConfiguration(itemTypeConfiguration);
                    editorPerm.setField(field);
                    editorPerm.setWorkflowStatus(workflowStatus);
                    editorPerm.setPermissionType(FieldStatusPermission.PermissionType.EDITORS);
                    editorPerm.setAssignedRoles(new HashSet<>());
                    
                    fieldStatusPermissionRepository.save(editorPerm);
                }

                // Viewer permission
                if (!fieldStatusPermissionRepository.existsByItemTypeConfigurationIdAndFieldIdAndWorkflowStatusIdAndPermissionType(
                        itemTypeConfiguration.getId(), fieldId, workflowStatus.getId(), 
                        FieldStatusPermission.PermissionType.VIEWERS)) {
                    
                    FieldStatusPermission viewerPerm = new FieldStatusPermission();
                    viewerPerm.setItemTypeConfiguration(itemTypeConfiguration);
                    viewerPerm.setField(field);
                    viewerPerm.setWorkflowStatus(workflowStatus);
                    viewerPerm.setPermissionType(FieldStatusPermission.PermissionType.VIEWERS);
                    viewerPerm.setAssignedRoles(new HashSet<>());
                    
                    fieldStatusPermissionRepository.save(viewerPerm);
                }
            }
        }
    }
}

package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
@Transactional
public class ItemTypePermissionService {

    private final WorkerPermissionRepository workerPermissionRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldEditorPermissionRepository fieldEditorPermissionRepository;
    private final CreatorPermissionRepository creatorPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;

    /**
     * Crea automaticamente tutte le permissions per un ItemTypeConfiguration
     */
    public void createPermissionsForItemTypeConfiguration(ItemTypeConfiguration itemTypeConfiguration) {
        // 1. Workers - uno per ItemType
        createWorkersPermission(itemTypeConfiguration);

        // 2. StatusOwner - uno per ogni WorkflowStatus
        createStatusOwnerPermissions(itemTypeConfiguration);

        // 3. FieldEditors - uno per ogni FieldConfiguration
        createFieldEditorPermissions(itemTypeConfiguration);

        // 4. Creators - uno per ItemType
        createCreatorPermission(itemTypeConfiguration);

        // 5. Executors - uno per ogni Transition del Workflow
        createExecutorPermissions(itemTypeConfiguration);

        // 6. Editors e Viewers - uno per ogni coppia (FieldConfiguration, WorkflowStatus)
        createFieldStatusPermissions(itemTypeConfiguration);
    }

    private void createWorkersPermission(ItemTypeConfiguration itemTypeConfiguration) {
        // Crea una WorkerPermission per ogni ItemTypeConfiguration
        if (!workerPermissionRepository.findAllByItemTypeConfiguration(itemTypeConfiguration).isEmpty()) {
            return; // Già esistente
        }
        
        WorkerPermission workerPermission = new WorkerPermission();
        workerPermission.setItemTypeConfiguration(itemTypeConfiguration);
        workerPermission.setAssignedRoles(new HashSet<>());
        workerPermission.setAssignedGrants(new HashSet<>());
        
        workerPermissionRepository.save(workerPermission);
    }

    private void createStatusOwnerPermissions(ItemTypeConfiguration itemTypeConfiguration) {
        // Crea una permission per ogni WorkflowStatus del workflow
        for (WorkflowStatus workflowStatus : itemTypeConfiguration.getWorkflow().getStatuses()) {
            if (!statusOwnerPermissionRepository.existsByItemTypeConfigurationIdAndWorkflowStatusId(
                    itemTypeConfiguration.getId(), workflowStatus.getId())) {
                
                StatusOwnerPermission permission = new StatusOwnerPermission();
                permission.setItemTypeConfiguration(itemTypeConfiguration);
                permission.setWorkflowStatus(workflowStatus);
                permission.setAssignedRoles(new HashSet<>());
                permission.setAssignedGrants(new HashSet<>());
                
                statusOwnerPermissionRepository.save(permission);
            }
        }
    }

    private void createFieldEditorPermissions(ItemTypeConfiguration itemTypeConfiguration) {
        // Crea una permission per ogni FieldConfiguration del FieldSet
        for (FieldSetEntry entry : itemTypeConfiguration.getFieldSet().getFieldSetEntries()) {
            FieldConfiguration fieldConfig = entry.getFieldConfiguration();
            
            if (!fieldEditorPermissionRepository.existsByItemTypeConfigurationIdAndFieldConfigurationId(
                    itemTypeConfiguration.getId(), fieldConfig.getId())) {
                
                FieldEditorPermission permission = new FieldEditorPermission();
                permission.setItemTypeConfiguration(itemTypeConfiguration);
                permission.setFieldConfiguration(fieldConfig);
                permission.setAssignedRoles(new HashSet<>());
                permission.setAssignedGrants(new HashSet<>());
                
                fieldEditorPermissionRepository.save(permission);
            }
        }
    }

    private void createCreatorPermission(ItemTypeConfiguration itemTypeConfiguration) {
        if (!creatorPermissionRepository.existsByItemTypeConfigurationId(itemTypeConfiguration.getId())) {
            CreatorPermission permission = new CreatorPermission();
            permission.setItemTypeConfiguration(itemTypeConfiguration);
            permission.setAssignedRoles(new HashSet<>());
            permission.setAssignedGrants(new HashSet<>());
            
            creatorPermissionRepository.save(permission);
        }
    }

    private void createExecutorPermissions(ItemTypeConfiguration itemTypeConfiguration) {
        // Crea una permission per ogni Transition del Workflow
        for (Transition transition : itemTypeConfiguration.getWorkflow().getTransitions()) {
            if (!executorPermissionRepository.existsByItemTypeConfigurationIdAndTransitionId(
                    itemTypeConfiguration.getId(), transition.getId())) {
                
                ExecutorPermission permission = new ExecutorPermission();
                permission.setItemTypeConfiguration(itemTypeConfiguration);
                permission.setTransition(transition);
                permission.setAssignedRoles(new HashSet<>());
                permission.setAssignedGrants(new HashSet<>());
                
                executorPermissionRepository.save(permission);
            }
        }
    }

    private void createFieldStatusPermissions(ItemTypeConfiguration itemTypeConfiguration) {
        // Crea una permission per ogni coppia (FieldConfiguration, WorkflowStatus)
        for (FieldSetEntry entry : itemTypeConfiguration.getFieldSet().getFieldSetEntries()) {
            FieldConfiguration fieldConfig = entry.getFieldConfiguration();
            
            for (WorkflowStatus workflowStatus : itemTypeConfiguration.getWorkflow().getStatuses()) {
                // Editor permission
                if (!fieldStatusPermissionRepository.existsByItemTypeConfigurationIdAndFieldConfigurationIdAndWorkflowStatusIdAndPermissionType(
                        itemTypeConfiguration.getId(), fieldConfig.getId(), workflowStatus.getId(), 
                        FieldStatusPermission.PermissionType.EDITOR)) {
                    
                    FieldStatusPermission editorPerm = new FieldStatusPermission();
                    editorPerm.setItemTypeConfiguration(itemTypeConfiguration);
                    editorPerm.setFieldConfiguration(fieldConfig);
                    editorPerm.setWorkflowStatus(workflowStatus);
                    editorPerm.setPermissionType(FieldStatusPermission.PermissionType.EDITOR);
                    editorPerm.setAssignedRoles(new HashSet<>());
                    editorPerm.setAssignedGrants(new HashSet<>());
                    
                    fieldStatusPermissionRepository.save(editorPerm);
                }

                // Viewer permission
                if (!fieldStatusPermissionRepository.existsByItemTypeConfigurationIdAndFieldConfigurationIdAndWorkflowStatusIdAndPermissionType(
                        itemTypeConfiguration.getId(), fieldConfig.getId(), workflowStatus.getId(), 
                        FieldStatusPermission.PermissionType.VIEWER)) {
                    
                    FieldStatusPermission viewerPerm = new FieldStatusPermission();
                    viewerPerm.setItemTypeConfiguration(itemTypeConfiguration);
                    viewerPerm.setFieldConfiguration(fieldConfig);
                    viewerPerm.setWorkflowStatus(workflowStatus);
                    viewerPerm.setPermissionType(FieldStatusPermission.PermissionType.VIEWER);
                    viewerPerm.setAssignedRoles(new HashSet<>());
                    viewerPerm.setAssignedGrants(new HashSet<>());
                    
                    fieldStatusPermissionRepository.save(viewerPerm);
                }
            }
        }
    }
}

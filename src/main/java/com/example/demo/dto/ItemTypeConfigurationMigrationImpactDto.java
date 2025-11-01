package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO per il report interattivo di migrazione permission quando cambiano
 * FieldSet e/o Workflow in un ItemTypeConfiguration
 */
@Data
@Builder
public class ItemTypeConfigurationMigrationImpactDto {
    
    private Long itemTypeConfigurationId;
    private String itemTypeConfigurationName;
    private Long itemTypeSetId;
    private String itemTypeSetName;
    private Long itemTypeId;
    private String itemTypeName;
    
    // Informazioni su cosa sta cambiando
    private FieldSetInfo oldFieldSet;
    private FieldSetInfo newFieldSet;
    private boolean fieldSetChanged;
    
    private WorkflowInfo oldWorkflow;
    private WorkflowInfo newWorkflow;
    private boolean workflowChanged;
    
    // Permission con flag di preservabilità
    private List<SelectablePermissionImpact> fieldOwnerPermissions;
    private List<SelectablePermissionImpact> statusOwnerPermissions;
    private List<SelectablePermissionImpact> fieldStatusPermissions;
    private List<SelectablePermissionImpact> executorPermissions;
    
    // Statistiche
    private int totalPreservablePermissions;
    private int totalRemovablePermissions;
    private int totalNewPermissions;
    private int totalPermissionsWithRoles;
    
    @Data
    @Builder
    public static class FieldSetInfo {
        private Long fieldSetId;
        private String fieldSetName;
        private List<FieldInfo> fields; // Lista dei Field (non FieldConfiguration!)
    }
    
    @Data
    @Builder
    public static class FieldInfo {
        private Long fieldId;
        private String fieldName;
    }
    
    @Data
    @Builder
    public static class WorkflowInfo {
        private Long workflowId;
        private String workflowName;
        private List<WorkflowStatusInfo> workflowStatuses;
        private List<TransitionInfo> transitions;
    }
    
    @Data
    @Builder
    public static class WorkflowStatusInfo {
        private Long workflowStatusId;
        private String workflowStatusName;
        private Long statusId;
        private String statusName;
    }
    
    @Data
    @Builder
    public static class TransitionInfo {
        private Long transitionId;
        private String transitionName;
        private Long fromWorkflowStatusId;
        private String fromWorkflowStatusName;
        private Long toWorkflowStatusId;
        private String toWorkflowStatusName;
    }
    
    /**
     * Permission impact con flag di preservabilità e checkbox
     */
    @Data
    @Builder
    public static class SelectablePermissionImpact {
        private Long permissionId;
        private String permissionType; // "FIELD_OWNERS", "STATUS_OWNERS", "EDITORS", "VIEWERS", "EXECUTORS"
        
        // Info entity attuale (Field, WorkflowStatus, Transition)
        private Long entityId; // FieldId, WorkflowStatusId, TransitionId
        private String entityName; // Nome del Field/Status/Transition
        
        // Per FieldStatusPermission: entrambe le entità
        private Long fieldId;
        private String fieldName;
        private Long workflowStatusId;
        private String workflowStatusName;
        
        // Info entity corrispondente nel nuovo stato (se canBePreserved)
        private Long matchingEntityId; // ID dell'entity equivalente nel nuovo stato
        private String matchingEntityName; // Nome dell'entity equivalente
        
        // Ruoli assegnati
        private List<String> assignedRoles;
        private boolean hasAssignments; // true se ha ruoli assegnati
        
        // Flag preservabilità
        private boolean canBePreserved; // true se esiste entity equivalente nel nuovo stato
        private boolean defaultPreserve; // true se dovrebbe essere preservata di default (canBePreserved && hasAssignments)
        
        // Info contesto
        private Long itemTypeSetId;
        private String itemTypeSetName;
        private Long projectId;
        private String projectName;
        
        // Suggerimento azione
        private String suggestedAction; // "PRESERVE", "REMOVE", "NEW"
    }
}



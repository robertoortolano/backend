package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FieldSetRemovalImpactDto {
    
    private Long fieldSetId;
    private String fieldSetName;
    private List<Long> removedFieldConfigurationIds;
    private List<String> removedFieldConfigurationNames;
    
    // ItemTypeSet coinvolti
    private List<ItemTypeSetImpact> affectedItemTypeSets;
    
    // Permissions che verranno rimosse
    private List<PermissionImpact> fieldOwnerPermissions;
    private List<PermissionImpact> fieldStatusPermissions;
    
    // Statistiche
    private int totalAffectedItemTypeSets;
    private int totalFieldOwnerPermissions;
    private int totalFieldStatusPermissions;
    private int totalGrantAssignments;
    private int totalRoleAssignments;
    
    @Data
    @Builder
    public static class ItemTypeSetImpact {
        private Long itemTypeSetId;
        private String itemTypeSetName;
        private Long projectId;
        private String projectName;
        
        // Informazioni aggregate per questo ItemTypeSet
        private int totalPermissions; // Totale permission che verranno rimosse
        private int totalRoleAssignments; // Totale ruoli assegnati
        private int totalGlobalGrants; // Totale grant globali
        private int totalProjectGrants; // Totale grant di progetto
        private List<ProjectImpact> projectImpacts; // Dettaglio per ogni progetto che usa questo ItemTypeSet
    }
    
    @Data
    @Builder
    public static class ProjectImpact {
        private Long projectId;
        private String projectName;
        private int projectGrantsCount; // Numero di grant di progetto per questo ItemTypeSet in questo progetto
    }
    
    @Data
    @Builder
    public static class PermissionImpact {
        private Long permissionId;
        private String permissionType; // "FIELD_OWNERS", "EDITORS", "VIEWERS", "ITEMTYPESET_ROLE"
        private Long itemTypeSetId;
        private String itemTypeSetName;
        private Long projectId;
        private String projectName;
        private Long fieldConfigurationId;
        private String fieldConfigurationName;
        private Long workflowStatusId;
        private String workflowStatusName;
        private Long roleId;
        private String roleName;
        private Long grantId; // Grant globale (se presente)
        private String grantName; // Nome grant globale
        private List<String> assignedRoles;
        private List<String> assignedGrants;
        private List<ProjectRoleInfo> projectAssignedRoles; // Ruoli di progetto per questa permission
        private boolean hasAssignments; // true se ha ruoli o grant assegnati
        
        // Info per preservazione (simile a ItemTypeConfigurationMigrationImpactDto)
        private Long fieldId; // ID del Field (per FieldOwnerPermission e FieldStatusPermission)
        private String fieldName; // Nome del Field
        private Long statusId; // ID dello Status (per FieldStatusPermission e StatusOwnerPermission)
        private String statusName; // Nome dello Status
        private Long matchingFieldId; // ID del Field corrispondente nel nuovo stato (se preservabile)
        private String matchingFieldName; // Nome del Field corrispondente nel nuovo stato
        private Long matchingStatusId; // ID dello Status corrispondente nel nuovo stato (se preservabile)
        private String matchingStatusName; // Nome dello Status corrispondente nel nuovo stato
        private boolean canBePreserved; // true se esiste entity equivalente nel nuovo stato
        private boolean defaultPreserve; // true se dovrebbe essere preservata di default (canBePreserved && hasAssignments)
        
        // Grant di progetto per questa permission (solo info minime - i dettagli vengono recuperati on-demand)
        private List<ProjectGrantInfo> projectGrants; // Lista di progetti che hanno grant per questa permission
    }
    
    @Data
    @Builder
    public static class ProjectGrantInfo {
        private Long projectId;
        private String projectName;
    }

    @Data
    @Builder
    public static class ProjectRoleInfo {
        private Long projectId;
        private String projectName;
        private List<String> roles;
    }
}

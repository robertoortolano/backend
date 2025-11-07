package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ItemTypeConfigurationRemovalImpactDto {
    
    private Long itemTypeSetId;
    private String itemTypeSetName;
    private List<Long> removedItemTypeConfigurationIds;
    private List<String> removedItemTypeConfigurationNames;
    
    // ItemTypeSet coinvolti (sempre l'ItemTypeSet stesso, ma include info progetto se applicabile)
    private List<ItemTypeSetImpact> affectedItemTypeSets;
    
    // Permissions che verranno rimosse
    private List<PermissionImpact> fieldOwnerPermissions;
    private List<PermissionImpact> statusOwnerPermissions;
    private List<PermissionImpact> fieldStatusPermissions;
    private List<PermissionImpact> executorPermissions;
    
    // Statistiche
    private int totalAffectedItemTypeSets;
    private int totalFieldOwnerPermissions;
    private int totalStatusOwnerPermissions;
    private int totalFieldStatusPermissions;
    private int totalExecutorPermissions;
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
        private int totalPermissions;
        private int totalRoleAssignments;
        private int totalGlobalGrants;
        private int totalProjectGrants;
        private List<ProjectImpact> projectImpacts;
    }
    
    @Data
    @Builder
    public static class ProjectImpact {
        private Long projectId;
        private String projectName;
        private int projectGrantsCount;
    }

    @Data
    @Builder
    public static class PermissionImpact {
        private Long permissionId;
        private String permissionType; // "FIELD_OWNERS", "STATUS_OWNERS", "EDITORS", "VIEWERS", "EXECUTORS", "WORKERS", "CREATORS"
        private Long itemTypeSetId;
        private String itemTypeSetName;
        private Long projectId;
        private String projectName;
        private Long itemTypeConfigurationId;
        private String itemTypeName;
        private String itemTypeCategory;
        private Long fieldConfigurationId;
        private String fieldConfigurationName;
        private Long workflowStatusId;
        private String workflowStatusName;
        private Long transitionId;
        private String transitionName;
        private String fromStatusName;
        private String toStatusName;
        private Long roleId;
        private String roleName;
        private Long grantId;
        private String grantName;
        private List<String> assignedRoles;
        private List<String> assignedGrants;
        private List<ProjectRoleInfo> projectAssignedRoles;
        private boolean hasAssignments; // true se ha ruoli o grant assegnati
        
        // Info per preservazione
        private Long fieldId;
        private String fieldName;
        private Long statusId;
        private String statusName;
        private Long matchingFieldId;
        private String matchingFieldName;
        private Long matchingStatusId;
        private String matchingStatusName;
        private boolean canBePreserved;
        private boolean defaultPreserve;
        
        // Grant di progetto per questa permission
        private List<ProjectGrantInfo> projectGrants;
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



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
    private List<PermissionImpact> itemTypeSetRoles;
    
    // Statistiche
    private int totalAffectedItemTypeSets;
    private int totalFieldOwnerPermissions;
    private int totalFieldStatusPermissions;
    private int totalItemTypeSetRoles;
    private int totalGrantAssignments;
    private int totalRoleAssignments;
    
    @Data
    @Builder
    public static class ItemTypeSetImpact {
        private Long itemTypeSetId;
        private String itemTypeSetName;
        private Long projectId;
        private String projectName;
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
        private Long grantId;
        private String grantName;
        private List<String> assignedRoles;
        private List<String> assignedGrants;
        private boolean hasAssignments; // true se ha ruoli o grant assegnati
    }
}

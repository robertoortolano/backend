package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StatusRemovalImpactDto {

    private Long workflowId;
    private String workflowName;
    private List<Long> removedStatusIds;
    private List<String> removedStatusNames;

    private List<ItemTypeSetImpact> affectedItemTypeSets;

    private List<PermissionImpact> statusOwnerPermissions;

    private int totalAffectedItemTypeSets;
    private int totalStatusOwnerPermissions;
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
        private String permissionType; // "STATUS_OWNERS"
        private Long itemTypeSetId;
        private String itemTypeSetName;
        private Long projectId;
        private String projectName;
        private Long workflowStatusId;
        private String workflowStatusName;
        private String statusName;
        private String statusCategory;
        private Long roleId;
        private String roleName;
        private Long grantId; // Grant globale (se presente)
        private String grantName; // Nome grant globale
        private List<String> assignedRoles;
        private boolean hasAssignments; // true se ha ruoli o grant assegnati
        
        // Info per preservazione
        private Long statusId; // ID dello Status
        private Long matchingStatusId; // ID dello Status corrispondente nel nuovo stato (se preservabile)
        private String matchingStatusName; // Nome dello Status corrispondente nel nuovo stato
        private boolean canBePreserved; // true se esiste entity equivalente nel nuovo stato
        private boolean defaultPreserve; // true se dovrebbe essere preservata di default
        
        // Grant di progetto per questa permission
        private List<ProjectGrantInfo> projectGrants;
    }
    
    @Data
    @Builder
    public static class ProjectGrantInfo {
        private Long projectId;
        private String projectName;
        private Long roleId; // ID dell'ItemTypeSetRole associato
    }
}




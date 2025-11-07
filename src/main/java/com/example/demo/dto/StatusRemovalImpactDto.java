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
    
    // Executor permissions per le transizioni che verranno rimosse insieme agli stati
    private List<ExecutorPermissionImpact> executorPermissions;
    
    // FieldStatus permissions (EDITORS/VIEWERS) per le coppie (Field, WorkflowStatus) che verranno rimosse
    private List<FieldStatusPermissionImpact> fieldStatusPermissions;
    
    private List<Long> removedTransitionIds;
    private List<String> removedTransitionNames;

    private int totalAffectedItemTypeSets;
    private int totalStatusOwnerPermissions;
    private int totalExecutorPermissions; // Permissions per transizioni rimosse
    private int totalFieldStatusPermissions; // Permissions EDITORS/VIEWERS rimosse
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
        // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata, ora usiamo permissionId e permissionType
        private Long grantId; // Grant globale (se presente)
        private String grantName; // Nome grant globale
        private List<String> assignedRoles; // Ruoli globali
        private List<ProjectRoleInfo> projectAssignedRoles; // Ruoli di progetto per ogni progetto
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
        // RIMOSSO: roleId - ItemTypeSetRole eliminata, ora usiamo permissionId e permissionType
    }
    
    @Data
    @Builder
    public static class ProjectRoleInfo {
        private Long projectId;
        private String projectName;
        private List<String> roles; // Ruoli di progetto per questo progetto
    }
    
    @Data
    @Builder
    public static class ExecutorPermissionImpact {
        private Long permissionId;
        private String permissionType; // "EXECUTORS"
        private Long itemTypeSetId;
        private String itemTypeSetName;
        private Long projectId;
        private String projectName;
        private Long transitionId;
        private String transitionName;
        private String fromStatusName;
        private String toStatusName;
        // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata, ora usiamo permissionId e permissionType
        private Long grantId; // Grant globale (se presente)
        private String grantName; // Nome grant globale
        private List<String> assignedRoles; // Ruoli globali
        private List<ProjectRoleInfo> projectAssignedRoles; // Ruoli di progetto per ogni progetto
        private boolean hasAssignments; // true se ha ruoli o grant assegnati
        
        // Info per preservazione
        private Long transitionIdMatch; // ID della Transition corrispondente nel nuovo stato (se preservabile)
        private String transitionNameMatch; // Nome della Transition corrispondente
        private boolean canBePreserved; // true se esiste entity equivalente nel nuovo stato
        private boolean defaultPreserve; // true se dovrebbe essere preservata di default
        
        // Grant di progetto per questa permission
        private List<ProjectGrantInfo> projectGrants;
    }
    
    @Data
    @Builder
    public static class FieldStatusPermissionImpact {
        private Long permissionId;
        private String permissionType; // "EDITORS" o "VIEWERS"
        private Long itemTypeSetId;
        private String itemTypeSetName;
        private Long projectId;
        private String projectName;
        private Long fieldId;
        private String fieldName;
        private Long workflowStatusId;
        private String workflowStatusName;
        private String statusName; // Nome dello Status
        // RIMOSSO: roleId e roleName - ItemTypeSetRole eliminata, ora usiamo permissionId e permissionType
        private Long grantId; // Grant globale (se presente)
        private String grantName; // Nome grant globale
        private List<String> assignedRoles; // Ruoli globali
        private List<ProjectRoleInfo> projectAssignedRoles; // Ruoli di progetto per ogni progetto
        private boolean hasAssignments; // true se ha ruoli o grant assegnati
        
        // Info per preservazione
        private Long matchingStatusId; // ID dello Status corrispondente nel nuovo workflow (se preservabile)
        private String matchingStatusName; // Nome dello Status corrispondente
        private boolean canBePreserved; // true se esiste Status equivalente nel nuovo workflow
        private boolean defaultPreserve; // true se dovrebbe essere preservata di default
        
        // Grant di progetto per questa permission
        private List<ProjectGrantInfo> projectGrants;
    }
}




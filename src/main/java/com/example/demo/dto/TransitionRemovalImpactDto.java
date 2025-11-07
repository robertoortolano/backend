package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TransitionRemovalImpactDto {
    
    private Long workflowId;
    private String workflowName;
    private List<Long> removedTransitionIds;
    private List<String> removedTransitionNames;
    
    // ItemTypeSet coinvolti
    private List<ItemTypeSetImpact> affectedItemTypeSets;
    
    // Permissions che verranno rimosse
    private List<PermissionImpact> executorPermissions;
    
    // Statistiche
    private int totalAffectedItemTypeSets;
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
}

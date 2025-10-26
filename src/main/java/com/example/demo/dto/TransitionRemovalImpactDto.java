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
        private String permissionType; // "EXECUTORS"
        private Long itemTypeSetId;
        private String itemTypeSetName;
        private Long projectId;
        private String projectName;
        private Long transitionId;
        private String transitionName;
        private String fromStatusName;
        private String toStatusName;
        private Long roleId;
        private String roleName;
        private List<String> assignedRoles;
        private boolean hasAssignments; // true se ha ruoli assegnati
    }
}

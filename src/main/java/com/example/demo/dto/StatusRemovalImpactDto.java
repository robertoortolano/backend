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
        private String permissionType; // "STATUS_OWNERS"
        private Long itemTypeSetId;
        private String itemTypeSetName;
        private Long projectId;
        private String projectName;
        private Long workflowStatusId;
        private String workflowStatusName;
        private String statusName;
        private String statusCategory;
        private List<String> assignedRoles;
        private boolean hasAssignments; // true se ha ruoli assegnati
    }
}


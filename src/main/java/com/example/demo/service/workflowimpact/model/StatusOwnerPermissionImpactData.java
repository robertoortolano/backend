package com.example.demo.service.workflowimpact.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class StatusOwnerPermissionImpactData {
    Long permissionId;
    String permissionType;
    Long itemTypeSetId;
    String itemTypeSetName;
    Long projectId;
    String projectName;
    Long workflowStatusId;
    String workflowStatusName;
    String statusName;
    String statusCategory;
    Long grantId;
    String grantName;
    @Singular("assignedRole")
    List<String> assignedRoles;
    boolean hasAssignments;
    boolean canBePreserved;
    boolean defaultPreserve;
    @Singular("projectGrant")
    List<ProjectGrantData> projectGrants;
}








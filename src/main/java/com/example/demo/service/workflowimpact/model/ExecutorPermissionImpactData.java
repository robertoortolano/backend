package com.example.demo.service.workflowimpact.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ExecutorPermissionImpactData {
    Long permissionId;
    String permissionType;
    Long itemTypeSetId;
    String itemTypeSetName;
    Long projectId;
    String projectName;
    Long transitionId;
    String transitionName;
    String fromStatusName;
    String toStatusName;
    Long grantId;
    String grantName;
    @Singular("assignedRole")
    List<String> assignedRoles;
    boolean hasAssignments;
    Long transitionIdMatch;
    String transitionNameMatch;
    boolean canBePreserved;
    boolean defaultPreserve;
    @Singular("projectGrant")
    List<ProjectGrantData> projectGrants;
}










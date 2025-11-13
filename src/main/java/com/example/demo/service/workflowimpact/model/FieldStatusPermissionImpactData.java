package com.example.demo.service.workflowimpact.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class FieldStatusPermissionImpactData {
    Long permissionId;
    String permissionType;
    Long itemTypeSetId;
    String itemTypeSetName;
    Long projectId;
    String projectName;
    Long fieldId;
    String fieldName;
    Long workflowStatusId;
    String workflowStatusName;
    String statusName;
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








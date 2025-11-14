package com.example.demo.service.permission.scope;

import com.example.demo.entity.PermissionAssignment;
import com.example.demo.exception.ApiException;
import com.example.demo.service.ProjectPermissionAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProjectPermissionScopeHandler implements PermissionScopeHandler {

    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;

    @Override
    public PermissionScope getScope() {
        return PermissionScope.PROJECT;
    }

    @Override
    public Map<Long, PermissionAssignment> getAssignments(
            String permissionType,
            Collection<Long> permissionIds,
            PermissionScopeRequest request
    ) {
        if (request == null || request.tenant() == null || request.projectId() == null) {
            throw new ApiException("Project scope requires tenant and project information");
        }
        return projectPermissionAssignmentService.getProjectAssignments(
                permissionType,
                permissionIds,
                request.projectId(),
                request.tenant()
        );
    }

    @Override
    public void deleteAssignment(String permissionType, Long permissionId, PermissionScopeRequest request) {
        if (request == null || request.tenant() == null || request.projectId() == null) {
            throw new ApiException("Project scope requires tenant and project information");
        }
        projectPermissionAssignmentService.deleteProjectAssignment(
                permissionType,
                permissionId,
                request.projectId(),
                request.tenant()
        );
    }
}









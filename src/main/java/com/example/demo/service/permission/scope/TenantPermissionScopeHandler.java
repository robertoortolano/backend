package com.example.demo.service.permission.scope;

import com.example.demo.entity.PermissionAssignment;
import com.example.demo.exception.ApiException;
import com.example.demo.service.PermissionAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TenantPermissionScopeHandler implements PermissionScopeHandler {

    private final PermissionAssignmentService permissionAssignmentService;

    @Override
    public PermissionScope getScope() {
        return PermissionScope.TENANT;
    }

    @Override
    public Map<Long, PermissionAssignment> getAssignments(
            String permissionType,
            Collection<Long> permissionIds,
            PermissionScopeRequest request
    ) {
        if (request == null || request.tenant() == null) {
            throw new ApiException("Tenant scope requires tenant information");
        }
        return permissionAssignmentService.getAssignments(permissionType, permissionIds, request.tenant());
    }

    @Override
    public void addRole(String permissionType, Long permissionId, Long roleId, PermissionScopeRequest request) {
        if (request == null || request.tenant() == null) {
            throw new ApiException("Tenant scope requires tenant information");
        }
        permissionAssignmentService.addRole(permissionType, permissionId, roleId, request.tenant());
    }

    @Override
    public void removeRole(String permissionType, Long permissionId, Long roleId, PermissionScopeRequest request) {
        if (request == null || request.tenant() == null) {
            throw new ApiException("Tenant scope requires tenant information");
        }
        permissionAssignmentService.removeRole(permissionType, permissionId, roleId, request.tenant());
    }

    @Override
    public void deleteAssignment(String permissionType, Long permissionId, PermissionScopeRequest request) {
        if (request == null || request.tenant() == null) {
            throw new ApiException("Tenant scope requires tenant information");
        }
        permissionAssignmentService.deleteAssignment(permissionType, permissionId, request.tenant());
    }
}








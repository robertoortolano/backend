package com.example.demo.service.permission.scope;

import com.example.demo.entity.PermissionAssignment;

import java.util.Collection;
import java.util.Map;

public interface PermissionScopeHandler {

    PermissionScope getScope();

    Map<Long, PermissionAssignment> getAssignments(
            String permissionType,
            Collection<Long> permissionIds,
            PermissionScopeRequest request
    );

    default void addRole(String permissionType, Long permissionId, Long roleId, PermissionScopeRequest request) {
        throw new UnsupportedOperationException("Role assignment is not supported for scope " + getScope());
    }

    default void removeRole(String permissionType, Long permissionId, Long roleId, PermissionScopeRequest request) {
        throw new UnsupportedOperationException("Role removal is not supported for scope " + getScope());
    }

    default void deleteAssignment(String permissionType, Long permissionId, PermissionScopeRequest request) {
        throw new UnsupportedOperationException("Assignment deletion is not supported for scope " + getScope());
    }
}









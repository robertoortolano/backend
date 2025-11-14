package com.example.demo.service.permission.role;

import com.example.demo.service.permission.scope.PermissionScope;
import com.example.demo.service.permission.scope.PermissionScopeRequest;

public interface PermissionRoleStrategy {

    String getPermissionType();

    PermissionScope getScope();

    void assignRole(Long permissionId, Long roleId, PermissionScopeRequest request);

    void removeRole(Long permissionId, Long roleId, PermissionScopeRequest request);
}









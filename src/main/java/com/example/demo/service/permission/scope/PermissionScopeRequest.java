package com.example.demo.service.permission.scope;

import com.example.demo.entity.Tenant;

public record PermissionScopeRequest(
        Tenant tenant,
        Long projectId
) {

    public static PermissionScopeRequest forTenant(Tenant tenant) {
        return new PermissionScopeRequest(tenant, null);
    }

    public static PermissionScopeRequest forProject(Tenant tenant, Long projectId) {
        return new PermissionScopeRequest(tenant, projectId);
    }
}








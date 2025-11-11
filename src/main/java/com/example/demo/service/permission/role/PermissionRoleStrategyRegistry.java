package com.example.demo.service.permission.role;

import com.example.demo.exception.ApiException;
import com.example.demo.service.permission.scope.PermissionScope;
import com.example.demo.service.permission.scope.PermissionScopeRegistry;
import com.example.demo.service.permission.scope.PermissionScopeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class PermissionRoleStrategyRegistry {

    private static final List<String> TENANT_PERMISSION_TYPES = List.of(
            "WorkerPermission",
            "StatusOwnerPermission",
            "FieldOwnerPermission",
            "CreatorPermission",
            "ExecutorPermission",
            "FieldStatusPermission"
    );

    private final PermissionScopeRegistry permissionScopeRegistry;
    private final Map<String, PermissionRoleStrategy> strategies = new ConcurrentHashMap<>();

    public PermissionRoleStrategy getStrategy(String permissionType) {
        if (permissionType == null) {
            throw new ApiException("Permission type must be provided");
        }
        return strategies.computeIfAbsent(permissionType, this::createStrategy);
    }

    private PermissionRoleStrategy createStrategy(String permissionType) {
        if (TENANT_PERMISSION_TYPES.contains(permissionType)) {
            return new SimplePermissionRoleStrategy(permissionType, PermissionScope.TENANT, permissionScopeRegistry);
        }
        throw new ApiException("Unknown permission type: " + permissionType
                + ". Supported types: " + String.join(", ", TENANT_PERMISSION_TYPES));
    }

    private static final class SimplePermissionRoleStrategy implements PermissionRoleStrategy {

        private final String permissionType;
        private final PermissionScope scope;
        private final PermissionScopeRegistry scopeRegistry;

        private SimplePermissionRoleStrategy(String permissionType,
                                             PermissionScope scope,
                                             PermissionScopeRegistry scopeRegistry) {
            this.permissionType = permissionType;
            this.scope = scope;
            this.scopeRegistry = scopeRegistry;
        }

        @Override
        public String getPermissionType() {
            return permissionType;
        }

        @Override
        public PermissionScope getScope() {
            return scope;
        }

        @Override
        public void assignRole(Long permissionId, Long roleId, PermissionScopeRequest request) {
            scopeRegistry.getHandler(scope).addRole(permissionType, permissionId, roleId, request);
        }

        @Override
        public void removeRole(Long permissionId, Long roleId, PermissionScopeRequest request) {
            scopeRegistry.getHandler(scope).removeRole(permissionType, permissionId, roleId, request);
        }
    }
}


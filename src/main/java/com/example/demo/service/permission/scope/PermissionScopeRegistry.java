package com.example.demo.service.permission.scope;

import com.example.demo.exception.ApiException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PermissionScopeRegistry {

    private final Map<PermissionScope, PermissionScopeHandler> handlers;

    public PermissionScopeRegistry(List<PermissionScopeHandler> handlers) {
        Map<PermissionScope, PermissionScopeHandler> map = new EnumMap<>(PermissionScope.class);
        for (PermissionScopeHandler handler : handlers) {
            map.put(handler.getScope(), handler);
        }
        this.handlers = Map.copyOf(map);
    }

    public PermissionScopeHandler getHandler(PermissionScope scope) {
        PermissionScopeHandler handler = handlers.get(scope);
        if (handler == null) {
            throw new ApiException("No handler registered for scope " + scope);
        }
        return handler;
    }
}



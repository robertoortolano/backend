package com.example.demo.service;

import com.example.demo.entity.Tenant;
// RIMOSSO: ItemTypeSetRoleType - ItemTypeSetRole eliminata
import com.example.demo.exception.ApiException;
// RIMOSSO: ProjectItemTypeSetRoleRole eliminata
import com.example.demo.service.permission.itemtypeset.ItemTypeSetPermissionProvisioningModule;
import com.example.demo.service.permission.itemtypeset.ItemTypeSetPermissionReportingModule;
import com.example.demo.service.permission.role.PermissionRoleStrategy;
import com.example.demo.service.permission.role.PermissionRoleStrategyRegistry;
import com.example.demo.service.permission.scope.PermissionScope;
import com.example.demo.service.permission.scope.PermissionScopeRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ItemTypeSetPermissionService {
    
    private final ItemTypeSetPermissionProvisioningModule provisioningModule;
    private final ItemTypeSetPermissionReportingModule reportingModule;
    private final PermissionRoleStrategyRegistry permissionRoleStrategyRegistry;
    
    /**
     * Crea automaticamente tutte le permissions per un ItemTypeSet
     */
    public void createPermissionsForItemTypeSet(Long itemTypeSetId, Tenant tenant) {
        provisioningModule.createPermissionsForItemTypeSet(itemTypeSetId, tenant);
    }
    
    /**
     * Ottiene tutte le permissions per un ItemTypeSet, raggruppate per tipo.
     * Se projectId è specificato, include anche le grant di progetto.
     */
    public Map<String, List<Map<String, Object>>> getPermissionsByItemTypeSet(Long itemTypeSetId, Tenant tenant, Long projectId) {
        return reportingModule.getPermissionsByItemTypeSet(itemTypeSetId, tenant, projectId);
    }
    
    /**
     * Assegna un ruolo a una permission
     */
    public void assignRoleToPermission(Long permissionId, Long roleId, String permissionType, Tenant tenant) {
        PermissionRoleStrategy strategy = permissionRoleStrategyRegistry.getStrategy(permissionType);
        if (strategy.getScope() != PermissionScope.TENANT) {
            throw new ApiException("Assign role is not supported for scope " + strategy.getScope());
        }
        strategy.assignRole(permissionId, roleId, PermissionScopeRequest.forTenant(tenant));
    }
    
    /**
     * Rimuove un ruolo da una permission
     */
    public void removeRoleFromPermission(Long permissionId, Long roleId, String permissionType, Tenant tenant) {
        PermissionRoleStrategy strategy = permissionRoleStrategyRegistry.getStrategy(permissionType);
        if (strategy.getScope() != PermissionScope.TENANT) {
            throw new ApiException("Remove role is not supported for scope " + strategy.getScope());
        }
        strategy.removeRole(permissionId, roleId, PermissionScopeRequest.forTenant(tenant));
    }
    
    // RIMOSSO: Metodi obsoleti - ItemTypeSetRole e ProjectItemTypeSetRoleGrant/ProjectItemTypeSetRoleRole eliminate
    // Le grant di progetto sono ora gestite tramite ProjectPermissionAssignmentService
}
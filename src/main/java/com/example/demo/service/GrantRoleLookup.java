package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.GrantRoleAssignmentRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service per lookup di Grant e Role Assignment.
 * Nota: Questo service gestisce il sistema Grant/GrantRoleAssignment per le Permission
 * e per i ruoli PROJECT-level. I ruoli TENANT-level (ADMIN/USER) sono gestiti da UserRole.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrantRoleLookup {

    private final GrantRoleAssignmentRepository grantRoleAssignmentRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    /**
     * Verifica se un utente ha un ruolo TENANT-level (es: ADMIN).
     * AGGIORNATO: Ora usa UserRoleRepository invece di GrantRoleAssignmentRepository.
     */
    public boolean existsByUserGlobal(User user, Tenant tenant, String roleName) {
        return userRoleRepository.existsByUserIdAndTenantIdAndRoleName(
                user.getId(), tenant.getId(), roleName
        );
    }

    /**
     * Ottiene un Role custom per nome (senza scope).
     * Nota: Role custom non hanno più campo scope.
     */
    Role getRoleByName(String roleName, Tenant tenant) {
        return roleRepository.findByNameAndTenant(roleName, tenant)
                .orElseThrow(() -> new ApiException("Role not found: " + roleName));
    }

    /**
     * @deprecated Metodo deprecato - Role non ha più campo scope.
     * Usa getRoleByName() invece.
     */
    @Deprecated
    Role getRoleByNameAndScope(String roleName, ScopeType scopeType, Tenant tenant) {
        // Per backward compatibility, ignora lo scope e cerca solo per nome
        return getRoleByName(roleName, tenant);
    }


    public List<Project> getProjectsByUserAndProjectRole(User user, Tenant tenant, String roleName) {
        return grantRoleAssignmentRepository.findProjectsByUserTenantAndRoleProject(
                user.getId(), tenant.getId(), roleName
        );
    }

    public List<GrantRoleAssignment> getAllByUser(User user, Tenant tenant) {
        return grantRoleAssignmentRepository.findAllByUserAndTenant(user.getId(), tenant.getId());
    }

}

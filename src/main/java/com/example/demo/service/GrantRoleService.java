package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service per gestione Grant e Role Assignment.
 * Nota: I ruoli TENANT-level (ADMIN/USER) sono ora gestiti da UserRole.
 * Questo service è principalmente per Permission e PROJECT-level roles.
 */
@Service
@RequiredArgsConstructor
public class GrantRoleService {

    private final GrantRoleLookup grantRoleLookup;
    private final UserRoleRepository userRoleRepository;

    /**
     * Restituisce i ruoli TENANT-level effettivi assegnati a un utente.
     * AGGIORNATO: Ora usa UserRoleRepository invece di GrantRoleAssignment.
     */
    @Transactional(readOnly = true)
    public List<String> getGlobalRolesForUser(Tenant tenant, User user) {
        return userRoleRepository.findTenantRolesByUserAndTenant(user.getId(), tenant.getId())
                .stream()
                .map(UserRole::getRoleName)
                .distinct()
                .toList();
    }

}

package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.repository.GrantRoleAssignmentRepository;
import com.example.demo.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GrantRoleService {

    private final GrantRoleLookup grantRoleLookup;

    /**
     * Restituisce i ruoli GLOBALI effettivi assegnati a un utente.
     */
    @Transactional(readOnly = true)
    public List<String> getGlobalRolesForUser(Tenant tenant, User user) {
        return grantRoleLookup.getAllByUser(user, tenant).stream()
                .map(GrantRoleAssignment::getRole)
                .filter(role -> role.getScope() == ScopeType.TENANT)
                .map(Role::getName)
                .distinct()
                .toList();
    }

}

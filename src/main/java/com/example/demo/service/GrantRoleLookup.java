package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.GrantRoleAssignmentRepository;
import com.example.demo.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrantRoleLookup {

    private final GrantRoleAssignmentRepository grantRoleAssignmentRepository;
    private final RoleRepository roleRepository;

    public boolean existsByUserGlobal(User user, Tenant tenant, String roleName) {
        return grantRoleAssignmentRepository.existsByUserAndTenantAndRoleGlobal(
                user.getId(), tenant.getId(), roleName
        );
    }


    Role getRoleByNameAndScope(String roleName, ScopeType scopeType, Tenant tenant) {
        return roleRepository.findByNameAndScopeAndTenant(roleName, scopeType, tenant)
                .orElseThrow(() -> new ApiException("Role not found"));
    }


    public List<Project> getProjectsByUserAndProjectRole(User user, Tenant tenant, String roleName) {
        return grantRoleAssignmentRepository.findProjectsByUserTenantAndRoleProject(
                user.getId(), tenant.getId(), roleName
        );
    }

    public List<GrantRoleAssignment> getAllByUser(User user, Tenant tenant) {
        return grantRoleAssignmentRepository.findAllByUserAndTenant(user.getId(), tenant.getId());
    }


    GrantRoleAssignment getByRoleAndScope(Role role,ScopeType scopeType, Tenant tenant) {
        return grantRoleAssignmentRepository.findFirstByStringAndScopeAndTenant(role.getName(),scopeType, tenant)
                .orElseThrow(() -> new ApiException("Grant Role Assignment not found"));
    }

}

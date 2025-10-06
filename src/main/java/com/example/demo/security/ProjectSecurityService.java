package com.example.demo.security;

import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.repository.GrantRoleAssignmentRepository;
import com.example.demo.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional(readOnly = true)
@AllArgsConstructor
public class ProjectSecurityService {

    private static final String USER_NOT_FOUND = "User not found";
    private final UserRepository userRepository;
    private final GrantRoleAssignmentRepository grantRoleAssignmentRepository;

    public boolean hasRoleNameInAnyProject(User user, Tenant tenant, String roleName) {
        return grantRoleAssignmentRepository.existsByUserAndTenantAndRoleProject(user.getId(), tenant.getId(), roleName );
    }

    public boolean hasProjectRole(User user, Tenant tenant, Long projectId, String roleName) {
        return grantRoleAssignmentRepository.existsByUserAndTenantAndProjectAndRoleProject(user.getId(), tenant.getId(), projectId, roleName);
    }









}
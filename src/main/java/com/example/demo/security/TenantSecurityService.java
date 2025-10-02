package com.example.demo.security;

import com.example.demo.entity.*;
import com.example.demo.enums.RoleName;
import com.example.demo.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional(readOnly = true)
@AllArgsConstructor
public class TenantSecurityService {

    private final UserRepository userRepository;
    private final GrantRoleAssignmentRepository grantRoleAssignmentRepository;
    private final ItemTypeRepository itemTypeRepository;
    private final ProjectRepository projectRepository;


    public boolean hasTenantRoleName(User user, Tenant tenant, RoleName roleName) {
        return grantRoleAssignmentRepository.existsByUserAndTenantAndRoleGlobal(user.getId(), tenant.getId(), roleName);
    }








}


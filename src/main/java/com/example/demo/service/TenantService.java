package com.example.demo.service;

import com.example.demo.dto.TenantDTO;
import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.initializer.TenantInitializer;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.*;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.security.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserRoleRepository userRoleRepository;
    private final DtoMapperFacade dtoMapper;
    private final List<TenantInitializer> tenantInitializers;

    public Tenant createTenant(Tenant tenant) {
        return tenantRepository.save(tenant);
    }

    @Transactional(readOnly = true)
    public List<TenantDTO> getTenantsByUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException("User not found"));

        return dtoMapper.toTenantDtos(userRoleRepository.findTenantsByUserId(user.getId()));
    }

    @Transactional
    public String createTenantForCurrentUser(User user, String licenseKey, String subdomain) {
        // Validate license key (for now, just check if it's not empty)
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            throw new ApiException("License key is required");
        }

        // Check if license key is already used
        if (tenantRepository.existsByLicenseKey(licenseKey)) {
            throw new ApiException("License key is already in use");
        }

        // Check if subdomain already exists
        if (tenantRepository.findBySubdomain(subdomain).isPresent()) {
            throw new ApiException("Subdomain already exists");
        }

        // Reload user to ensure it's managed by the current Hibernate session
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Create tenant
        Tenant tenant = new Tenant();
        tenant.setSubdomain(subdomain);
        tenant.setName(subdomain); // Use subdomain as name for now
        tenant.setLicenseKey(licenseKey);
        
        // Save tenant first
        tenant = tenantRepository.save(tenant);

        // Initialize tenant with default data (Fields, Statuses, Workflows, etc.)
        for (TenantInitializer initializer : tenantInitializers) {
            initializer.initialize(tenant);
        }

        // Nota: I ruoli ADMIN e USER sono ora gestiti tramite UserRole (entity separata)
        // Qui possiamo creare eventuali ruoli custom di default per le Permission

        // Assign ADMIN role to the creator via UserRole
        UserRole adminUserRole = UserRole.builder()
                .user(managedUser)
                .tenant(tenant)
                .roleName("ADMIN")
                .scope(ScopeType.TENANT)
                .project(null)
                .build();
        userRoleRepository.save(adminUserRole);

        // Load the user roles for the token
        List<UserRole> userRoles = userRoleRepository.findByUserIdAndTenantId(
                managedUser.getId(), tenant.getId());

        // Generate new token with tenantId and roles
        CustomUserDetails userDetails = new CustomUserDetails(managedUser, userRoles);
        return jwtTokenUtil.generateAccessTokenWithTenantId(userDetails, tenant.getId());
    }

    @Transactional(readOnly = true)
    public String selectTenant(User user, Long tenantId) {
        // Verify tenant exists
        if (!tenantRepository.existsById(tenantId)) {
            throw new ApiException("Tenant not found");
        }

        // Verify user has access to this tenant via UserRole
        if (!userRoleRepository.hasAccessToTenant(user.getId(), tenantId)) {
            throw new ApiException("You don't have access to this tenant");
        }

        // Reload user to ensure it's managed by the current Hibernate session
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Load user's roles for this tenant
        List<UserRole> userRoles = userRoleRepository.findByUserIdAndTenantId(
                managedUser.getId(), tenantId);

        // Generate new token with tenantId and roles
        CustomUserDetails userDetails = new CustomUserDetails(managedUser, userRoles);
        return jwtTokenUtil.generateAccessTokenWithTenantId(userDetails, tenantId);
    }

    /**
     * @deprecated Usa TenantUserManagementService.assignRole() invece.
     * Mantenuto per backward compatibility con vecchi controller.
     */
    @Deprecated
    @Transactional
    public void assignUserToTenant(String username, Tenant tenant, User currentUser) {
        // Check if current user has access to the tenant
        if (!userRoleRepository.hasAccessToTenant(currentUser.getId(), tenant.getId())) {
            throw new ApiException("You don't have access to this tenant");
        }
        
        // Find the user to assign
        User userToAssign = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if user already has USER role in this tenant
        if (userRoleRepository.existsByUserIdAndTenantIdAndRoleName(
                userToAssign.getId(), tenant.getId(), "USER")) {
            return; // User already has access
        }

        // Assign USER role to the user
        UserRole newUserRole = UserRole.builder()
                .user(userToAssign)
                .tenant(tenant)
                .roleName("USER")
                .scope(ScopeType.TENANT)
                .project(null)
                .build();
        userRoleRepository.save(newUserRole);
    }

}

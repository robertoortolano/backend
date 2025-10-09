package com.example.demo.service;

import com.example.demo.dto.AssignUserRequest;
import com.example.demo.dto.TenantDTO;
import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.initializer.TenantInitializer;
import com.example.demo.repository.*;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.security.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final JwtTokenUtil jwtTokenUtil;
    private final RoleRepository roleRepository;
    private final GrantRepository grantRepository;
    private final GrantRoleAssignmentRepository grantRoleAssignmentRepository;
    private final List<TenantInitializer> tenantInitializers;

    public Tenant createTenant(Tenant tenant) {
        return tenantRepository.save(tenant);
    }

    @Transactional(readOnly = true)
    public List<TenantDTO> getTenantsByUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return tenantRepository.findByUsersContaining(user).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public String createTenantForCurrentUser(User user, String licenseKey, String subdomain) {
        // Validate license key (for now, just check if it's not empty)
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            throw new RuntimeException("License key is required");
        }

        // Check if license key is already used
        if (tenantRepository.existsByLicenseKey(licenseKey)) {
            throw new RuntimeException("License key is already in use");
        }

        // Check if subdomain already exists
        if (tenantRepository.findBySubdomain(subdomain).isPresent()) {
            throw new RuntimeException("Subdomain already exists");
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
        
        // Add tenant to user (owning side of the relationship)
        managedUser.getTenants().add(tenant);
        userRepository.save(managedUser);

        // Initialize tenant with default data (Fields, Statuses, Workflows, etc.)
        for (TenantInitializer initializer : tenantInitializers) {
            initializer.initialize(tenant);
        }

        // Create ADMIN role with TENANT scope for the creator
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        adminRole.setDescription("Tenant Administrator");
        adminRole.setScope(ScopeType.TENANT);
        adminRole.setDefaultRole(true);
        adminRole.setTenant(tenant);
        adminRole = roleRepository.save(adminRole);

        // Create Grant for the user
        Grant adminGrant = new Grant();
        adminGrant.setRole(adminRole);
        adminGrant.setUsers(new HashSet<>(Set.of(managedUser)));
        adminGrant = grantRepository.save(adminGrant);

        // Create GrantRoleAssignment to link Grant, Role and Tenant
        GrantRoleAssignment assignment = new GrantRoleAssignment();
        assignment.setGrant(adminGrant);
        assignment.setRole(adminRole);
        assignment.setTenant(tenant);
        assignment.setProject(null); // Tenant-level, no project
        assignment = grantRoleAssignmentRepository.save(assignment);

        // Load the assignments for the token
        List<GrantRoleAssignment> assignments = grantRoleAssignmentRepository.findAllByUserAndTenant(
                managedUser.getId(), tenant.getId());

        // Generate new token with tenantId and roles
        CustomUserDetails userDetails = new CustomUserDetails(managedUser, assignments);
        return jwtTokenUtil.generateAccessTokenWithTenantId(userDetails, tenant.getId());
    }

    @Transactional(readOnly = true)
    public String selectTenant(User user, Long tenantId) {
        // Verify tenant exists
        if (!tenantRepository.existsById(tenantId)) {
            throw new RuntimeException("Tenant not found");
        }

        // Verify user has access to this tenant (without loading collections)
        if (!tenantRepository.existsByIdAndUserId(tenantId, user.getId())) {
            throw new RuntimeException("You don't have access to this tenant");
        }

        // Reload user to ensure it's managed by the current Hibernate session
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Load user's role assignments for this tenant
        List<GrantRoleAssignment> assignments = grantRoleAssignmentRepository.findAllByUserAndTenant(
                managedUser.getId(), tenantId);

        // Generate new token with tenantId and roles
        CustomUserDetails userDetails = new CustomUserDetails(managedUser, assignments);
        return jwtTokenUtil.generateAccessTokenWithTenantId(userDetails, tenantId);
    }

    @Transactional
    public void assignUserToTenant(AssignUserRequest request, Tenant tenant, User currentUser) {
        // Check if current user has access to the tenant (without loading collections)
        if (!tenantRepository.existsByIdAndUserId(tenant.getId(), currentUser.getId())) {
            throw new RuntimeException("You don't have access to this tenant");
        }
        
        // Find the user to assign
        User userToAssign = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if user is already assigned to this tenant (without loading collections)
        if (tenantRepository.existsByIdAndUserId(tenant.getId(), userToAssign.getId())) {
            return; // User already assigned
        }

        // Add tenant to user (owning side of the relationship)
        userToAssign.getTenants().add(tenant);
        userRepository.save(userToAssign);
    }

    private TenantDTO convertToDTO(Tenant tenant) {
        TenantDTO dto = new TenantDTO();
        dto.setId(tenant.getId());
        dto.setName(tenant.getName());
        dto.setSubdomain(tenant.getSubdomain());
        dto.setCreatedAt(tenant.getCreatedAt());
        return dto;
    }
}

package com.example.demo.security;

import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.repository.GrantRoleAssignmentRepository;
import com.example.demo.repository.TenantRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.tenant.TenantContext;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional(readOnly = true)
@AllArgsConstructor
public class ProjectSecurityService {

    private static final String USER_NOT_FOUND = "User not found";
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final GrantRoleAssignmentRepository grantRoleAssignmentRepository;

    public boolean hasRoleNameInAnyProject(User user, Tenant tenant, String roleName) {
        return grantRoleAssignmentRepository.existsByUserAndTenantAndRoleProject(user.getId(), tenant.getId(), roleName );
    }

    public boolean hasProjectRole(User user, Tenant tenant, Long projectId, String roleName) {
        return grantRoleAssignmentRepository.existsByUserAndTenantAndProjectAndRoleProject(user.getId(), tenant.getId(), projectId, roleName);
    }
    
    /**
     * Overload per uso in @PreAuthorize: estrae automaticamente User e Tenant dal contesto di sicurezza
     * Usa: @projectSecurityService.hasProjectRole(#projectId, 'PROJECT_ADMIN')
     */
    public boolean hasProjectRole(Long projectId, String roleName) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return false;
        }
        
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new SecurityException(USER_NOT_FOUND));
        
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) {
            return false;
        }
        
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new SecurityException("Tenant not found"));
        
        return hasProjectRole(user, tenant, projectId, roleName);
    }
    
    /**
     * Overload per uso in @PreAuthorize con principal e tenant espliciti
     * Usa: @projectSecurityService.hasProjectRole(principal, #tenant, #projectId, 'PROJECT_ADMIN')
     */
    public boolean hasProjectRole(Object principal, Tenant tenant, Long projectId, String roleName) {
        User user = extractUser(principal);
        return hasProjectRole(user, tenant, projectId, roleName);
    }
    
    private User extractUser(Object principal) {
        if (principal instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) principal;
            return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new SecurityException(USER_NOT_FOUND));
        }
        throw new SecurityException("Principal is not CustomUserDetails");
    }









}
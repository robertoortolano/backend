package com.example.demo.service;

import com.example.demo.dto.AssignUserRequest;
import com.example.demo.entity.*;
import com.example.demo.enums.RoleName;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.initializer.TenantInitializer;
import com.example.demo.repository.*;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.security.CustomUserDetailsService;
import com.example.demo.security.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private LicenseRepository licenseRepository;
    
    @Mock
    private RoleRepository roleRepository;
    
    @Mock
    private GrantRepository grantRepository;
    
    @Mock
    private GrantRoleAssignmentRepository grantRoleAssignmentRepository;
    
    @Mock
    private CustomUserDetailsService customUserDetailsService;
    
    @Mock
    private LicenseService licenseService;
    
    @Mock
    private GrantRoleLookup grantRoleLookup;
    
    @Mock
    private List<TenantInitializer> tenantInitializers;
    
    @Mock
    private JwtTokenUtil jwtTokenUtil;
    
    @InjectMocks
    private TenantService tenantService;

    private User user;
    private Tenant tenant;
    private License license;
    private Role adminRole;
    private Grant adminGrant;
    private GrantRoleAssignment assignment;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        // Setup user
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setFullName("Test User");

        // Setup tenant
        tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSubdomain("test-tenant");
        tenant.setTenantAdmin(user);

        // Setup license
        license = new License();
        license.setId(1L);
        license.setLicenseKey("test-license-key");
        license.setTenant(tenant);

        // Setup admin role
        adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setName("ADMIN");
        adminRole.setScope(ScopeType.TENANT);
        adminRole.setDefaultRole(true);
        adminRole.setTenant(tenant);

        // Setup admin grant
        adminGrant = new Grant();
        adminGrant.setId(1L);
        adminGrant.setUsers(new HashSet<>(Arrays.asList(user)));

        // Setup grant role assignment
        assignment = new GrantRoleAssignment();
        assignment.setId(1L);
        assignment.setGrant(adminGrant);
        assignment.setRole(adminRole);
        assignment.setTenant(tenant);

        // Setup user details
        userDetails = new CustomUserDetails(user, new ArrayList<>());

        // Set base domain
        ReflectionTestUtils.setField(tenantService, "baseDomain", "example.com");
    }

    @Test
    void testCreateTenantForCurrentUser_Success() {
        // Given
        String licenseKey = "test-license-key";
        String subdomain = "test-tenant";

        when(licenseService.exists(licenseKey)).thenReturn(false);
        when(licenseService.isValidLicenseKey(licenseKey)).thenReturn(true);
        when(tenantRepository.existsBySubdomain(subdomain)).thenReturn(false);
        when(licenseRepository.save(any(License.class))).thenReturn(license);
        when(roleRepository.save(any(Role.class))).thenReturn(adminRole);
        when(grantRepository.save(any(Grant.class))).thenReturn(adminGrant);
        when(grantRoleAssignmentRepository.save(any(GrantRoleAssignment.class))).thenReturn(assignment);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(customUserDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);
        when(jwtTokenUtil.generateAccessTokenWithTenantId(any(), any())).thenReturn("test-token");

        // When
        String result = tenantService.createTenantForCurrentUser(user, licenseKey, subdomain);

        // Then
        assertNotNull(result);
        assertEquals("test-token", result);
        verify(licenseService).exists(licenseKey);
        verify(licenseService).isValidLicenseKey(licenseKey);
        verify(tenantRepository).existsBySubdomain(subdomain);
        verify(licenseRepository, times(2)).save(any(License.class));
        verify(roleRepository).save(any(Role.class));
        verify(grantRepository).save(any(Grant.class));
        verify(grantRoleAssignmentRepository).save(any(GrantRoleAssignment.class));
        verify(userRepository).save(any(User.class));
        verify(jwtTokenUtil).generateAccessTokenWithTenantId(eq(userDetails), any());
    }

    @Test
    void testCreateTenantForCurrentUser_LicenseAlreadyExists_ThrowsException() {
        // Given
        String licenseKey = "test-license-key";
        String subdomain = "test-tenant";

        when(licenseService.exists(licenseKey)).thenReturn(true);

        // When & Then
        assertThrows(ApiException.class, () -> tenantService.createTenantForCurrentUser(user, licenseKey, subdomain));
    }

    @Test
    void testCreateTenantForCurrentUser_InvalidLicense_ThrowsException() {
        // Given
        String licenseKey = "invalid-license-key";
        String subdomain = "test-tenant";

        when(licenseService.exists(licenseKey)).thenReturn(false);
        when(licenseService.isValidLicenseKey(licenseKey)).thenReturn(false);

        // When & Then
        assertThrows(ApiException.class, () -> tenantService.createTenantForCurrentUser(user, licenseKey, subdomain));
    }

    @Test
    void testCreateTenantForCurrentUser_SubdomainAlreadyExists_ThrowsException() {
        // Given
        String licenseKey = "test-license-key";
        String subdomain = "test-tenant";

        when(licenseService.exists(licenseKey)).thenReturn(false);
        when(licenseService.isValidLicenseKey(licenseKey)).thenReturn(true);
        when(tenantRepository.existsBySubdomain(subdomain)).thenReturn(true);

        // When & Then
        assertThrows(ApiException.class, () -> tenantService.createTenantForCurrentUser(user, licenseKey, subdomain));
    }

    @Test
    void testAssignUserToTenant_Success() {
        // Given
        AssignUserRequest request = new AssignUserRequest("testuser", 1L, "USER");
        User adminUser = new User();
        adminUser.setId(2L);
        adminUser.setUsername("admin");

        GrantRoleAssignment adminAssignment = new GrantRoleAssignment();
        adminAssignment.setRole(adminRole);
        adminAssignment.setGrant(adminGrant);

        Role userRole = new Role();
        userRole.setId(2L);
        userRole.setName("USER");
        userRole.setScope(ScopeType.TENANT);
        userRole.setTenant(tenant);

        GrantRoleAssignment userAssignment = new GrantRoleAssignment();
        userAssignment.setRole(userRole);
        userAssignment.setGrant(adminGrant);

        when(grantRoleLookup.getAllByUser(adminUser, tenant)).thenReturn(Arrays.asList(adminAssignment));
        when(grantRoleLookup.getRoleByNameAndScope("USER", ScopeType.TENANT, tenant)).thenReturn(userRole);
        when(grantRoleLookup.existsByUserGlobal(adminUser, tenant, "USER")).thenReturn(false);
        when(grantRoleLookup.getByRoleAndScope(userRole, ScopeType.TENANT, tenant)).thenReturn(userAssignment);
        when(grantRepository.save(any(Grant.class))).thenReturn(adminGrant);

        // When
        tenantService.assignUserToTenant(request, tenant, adminUser);

        // Then
        verify(grantRoleLookup).getAllByUser(adminUser, tenant);
        verify(grantRoleLookup).getRoleByNameAndScope("USER", ScopeType.TENANT, tenant);
        verify(grantRoleLookup).existsByUserGlobal(adminUser, tenant, "USER");
        verify(grantRepository).save(any(Grant.class));
    }

    @Test
    void testAssignUserToTenant_NotAdmin_ThrowsException() {
        // Given
        AssignUserRequest request = new AssignUserRequest("testuser", 1L, "USER");
        User regularUser = new User();
        regularUser.setId(2L);
        regularUser.setUsername("regular");

        when(grantRoleLookup.getAllByUser(regularUser, tenant)).thenReturn(Collections.emptyList());

        // When & Then
        assertThrows(ApiException.class, () -> tenantService.assignUserToTenant(request, tenant, regularUser));
    }

    @Test
    void testAssignUserToTenant_AlreadyAssigned_ThrowsException() {
        // Given
        AssignUserRequest request = new AssignUserRequest("testuser", 1L, "USER");
        User adminUser = new User();
        adminUser.setId(2L);
        adminUser.setUsername("admin");

        GrantRoleAssignment adminAssignment = new GrantRoleAssignment();
        adminAssignment.setRole(adminRole);
        adminAssignment.setGrant(adminGrant);

        Role userRole = new Role();
        userRole.setId(2L);
        userRole.setName("USER");
        userRole.setScope(ScopeType.TENANT);
        userRole.setTenant(tenant);

        when(grantRoleLookup.getAllByUser(adminUser, tenant)).thenReturn(Arrays.asList(adminAssignment));
        when(grantRoleLookup.getRoleByNameAndScope("USER", ScopeType.TENANT, tenant)).thenReturn(userRole);
        when(grantRoleLookup.existsByUserGlobal(adminUser, tenant, "USER")).thenReturn(true);

        // When & Then
        assertThrows(ApiException.class, () -> tenantService.assignUserToTenant(request, tenant, adminUser));
    }

    @Test
    void testGetFirstByAdminUser_Success() {
        // Given
        when(tenantRepository.findFirstByTenantAdminUsername("testuser")).thenReturn(Optional.of(tenant));

        // When
        Tenant result = tenantService.getFirstByAdminUser("testuser");

        // Then
        assertNotNull(result);
        assertEquals(tenant, result);
        verify(tenantRepository).findFirstByTenantAdminUsername("testuser");
    }

    @Test
    void testGetFirstByAdminUser_NotFound_ReturnsNull() {
        // Given
        when(tenantRepository.findFirstByTenantAdminUsername("nonexistent")).thenReturn(Optional.empty());

        // When
        Tenant result = tenantService.getFirstByAdminUser("nonexistent");

        // Then
        assertNull(result);
        verify(tenantRepository).findFirstByTenantAdminUsername("nonexistent");
    }
}

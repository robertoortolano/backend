package com.example.demo.service;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.InvalidatedToken;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.TokenBlacklistRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.security.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;
    
    @Mock
    private TenantService tenantService;
    
    @Mock
    private JwtTokenUtil jwtTokenUtil;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private AuthenticationManager authenticationManager;
    
    @InjectMocks
    private UserService userService;

    private User user;
    private Tenant tenant;
    private CustomUserDetails userDetails;
    private Authentication authentication;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private HttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        // Setup user
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setFullName("Test User");
        user.setPasswordHash("encoded-password");

        // Setup tenant
        tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSubdomain("test-tenant");
        tenant.setTenantAdmin(user);

        // Setup user details
        userDetails = new CustomUserDetails(user, new ArrayList<>());

        // Setup authentication
        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        // Setup register request
        registerRequest = new RegisterRequest("testuser", "password123", "test-license-key", "test-tenant");

        // Setup login request
        loginRequest = new LoginRequest("testuser", "password123");

        // Setup HTTP request
        httpRequest = mock(HttpServletRequest.class);
    }

    @Test
    void testRegisterUser_Success() {
        // Given
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        userService.registerUser(registerRequest);

        // Then
        verify(userRepository).findByUsername("testuser");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testRegisterUser_UsernameAlreadyExists_ThrowsException() {
        // Given
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        // When & Then
        assertThrows(ApiException.class, () -> userService.registerUser(registerRequest));
        verify(userRepository).findByUsername("testuser");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testFindByUsername_Success() {
        // Given
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        // When
        Optional<User> result = userService.findByUsername("testuser");

        // Then
        assertTrue(result.isPresent());
        assertEquals(user, result.get());
        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void testFindByUsername_NotFound_ReturnsEmpty() {
        // Given
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // When
        Optional<User> result = userService.findByUsername("nonexistent");

        // Then
        assertTrue(result.isEmpty());
        verify(userRepository).findByUsername("nonexistent");
    }

    @Test
    void testAuthenticateUser_Success_WithTenant() {
        // Given
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tenantService.getFirstByAdminUser("testuser")).thenReturn(tenant);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtTokenUtil.generateAccessTokenWithTenantId(userDetails, 1L)).thenReturn("access-token");
        when(jwtTokenUtil.generateRefreshToken(userDetails)).thenReturn("refresh-token");

        // When
        LoginResponse result = userService.authenticateUser(loginRequest);

        // Then
        assertNotNull(result);
        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals("Login success", result.message());
        assertEquals(1L, result.tenantId());
        assertTrue(result.success());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(tenantService).getFirstByAdminUser("testuser");
        verify(userRepository).save(any(User.class));
        verify(jwtTokenUtil).generateAccessTokenWithTenantId(userDetails, 1L);
        verify(jwtTokenUtil).generateRefreshToken(userDetails);
    }

    @Test
    void testAuthenticateUser_Success_WithoutTenant() {
        // Given
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tenantService.getFirstByAdminUser("testuser")).thenReturn(null);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtTokenUtil.generateAccessTokenWithoutTenantId(userDetails)).thenReturn("access-token");
        when(jwtTokenUtil.generateRefreshToken(userDetails)).thenReturn("refresh-token");

        // When
        LoginResponse result = userService.authenticateUser(loginRequest);

        // Then
        assertNotNull(result);
        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals("Login success", result.message());
        assertNull(result.tenantId());
        assertTrue(result.success());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(tenantService).getFirstByAdminUser("testuser");
        verify(userRepository).save(any(User.class));
        verify(jwtTokenUtil).generateAccessTokenWithoutTenantId(userDetails);
        verify(jwtTokenUtil).generateRefreshToken(userDetails);
    }

    @Test
    void testInvalidateToken_Success() {
        // Given
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer test-token");
        when(tokenBlacklistRepository.save(any(InvalidatedToken.class))).thenReturn(new InvalidatedToken("test-token"));

        // When
        userService.invalidateToken(httpRequest);

        // Then
        verify(httpRequest).getHeader("Authorization");
        verify(tokenBlacklistRepository).save(any(InvalidatedToken.class));
    }

    @Test
    void testInvalidateToken_NoAuthorizationHeader_DoesNothing() {
        // Given
        when(httpRequest.getHeader("Authorization")).thenReturn(null);

        // When
        userService.invalidateToken(httpRequest);

        // Then
        verify(httpRequest).getHeader("Authorization");
        verify(tokenBlacklistRepository, never()).save(any(InvalidatedToken.class));
    }

    @Test
    void testInvalidateToken_InvalidHeaderFormat_DoesNothing() {
        // Given
        when(httpRequest.getHeader("Authorization")).thenReturn("InvalidFormat test-token");

        // When
        userService.invalidateToken(httpRequest);

        // Then
        verify(httpRequest).getHeader("Authorization");
        verify(tokenBlacklistRepository, never()).save(any(InvalidatedToken.class));
    }

    @Test
    void testFindFirstTenantByUsername_Success() {
        // Given
        when(tenantService.getFirstByAdminUser("testuser")).thenReturn(tenant);

        // When
        Tenant result = userService.findFirstTenantByUsername("testuser");

        // Then
        assertNotNull(result);
        assertEquals(tenant, result);
        verify(tenantService).getFirstByAdminUser("testuser");
    }

    @Test
    void testGetActiveTenantForUser_Success() {
        // Given
        user.setActiveTenant(tenant);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        // When
        Tenant result = userService.getActiveTenantForUser("testuser");

        // Then
        assertNotNull(result);
        assertEquals(tenant, result);
        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void testGetActiveTenantForUser_UserNotFound_ThrowsException() {
        // Given
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> userService.getActiveTenantForUser("nonexistent"));
        verify(userRepository).findByUsername("nonexistent");
    }

    @Test
    void testGetActiveTenantForUser_NoActiveTenant_ThrowsException() {
        // Given
        user.setActiveTenant(null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        // When & Then
        assertThrows(ApiException.class, () -> userService.getActiveTenantForUser("testuser"));
        verify(userRepository).findByUsername("testuser");
    }
}

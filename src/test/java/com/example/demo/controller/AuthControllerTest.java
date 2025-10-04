package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.enums.RoleName;
import com.example.demo.repository.TokenBlacklistRepository;
import com.example.demo.service.TenantService;
import com.example.demo.service.UserService;
import com.example.demo.security.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;
    
    @Mock
    private TenantService tenantService;
    
    @Mock
    private AuthenticationManager authenticationManager;
    
    @Mock
    private JwtTokenUtil jwtTokenUtil;
    
    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;
    
    @InjectMocks
    private AuthController authController;

    private User user;
    private Tenant tenant;
    private RegisterRequest registerRequest;
    private RegisterTenantRequest tenantRequest;
    private LoginRequest loginRequest;
    private LoginResponse loginResponse;
    private AssignUserRequest assignUserRequest;
    private HttpServletRequest httpRequest;

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

        // Setup register request
        registerRequest = new RegisterRequest(user.getUsername(), "password123", "test-license-key", tenant.getSubdomain());

        // Setup tenant request
        tenantRequest = new RegisterTenantRequest("test-tenant", "test-license-key");

        // Setup login request
        loginRequest = new LoginRequest(user.getUsername(), "password123");

        // Setup login response
        loginResponse = new LoginResponse("access-token", "refresh-token", "Login success", 1L, true);

        // Setup assign user request
        assignUserRequest = new AssignUserRequest(user.getUsername(), tenant.getId(), RoleName.ADMIN.name());

        // Setup HTTP request
        httpRequest = mock(HttpServletRequest.class);
    }

    @Test
    void testRegisterUser_Success() {
        // Given
        doNothing().when(userService).registerUser(registerRequest);

        // When
        ResponseEntity<ApiResponse> result = authController.registerUser(registerRequest);

        // Then
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("Utente registrato con successo", result.getBody().message());
        assertEquals(HttpStatus.OK.value(), result.getBody().status());
        verify(userService).registerUser(registerRequest);
    }

    @Test
    void testCreateTenant_Success() {
        // Given
        when(tenantService.createTenantForCurrentUser(user, "test-license-key", "test-tenant"))
                .thenReturn("new-token");

        // When
        ResponseEntity<Map<String, String>> result = authController.createTenant(tenantRequest, user);

        // Then
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("Tenant creato", result.getBody().get("Message"));
        assertEquals("test-tenant", result.getBody().get("subdomain"));
        assertEquals("new-token", result.getBody().get("token"));
        verify(tenantService).createTenantForCurrentUser(user, "test-license-key", "test-tenant");
    }

    @Test
    void testAssignUserToTenant_Success() {
        // Given
        doNothing().when(tenantService).assignUserToTenant(assignUserRequest, tenant, user);

        // When
        ResponseEntity<ApiResponse> result = authController.assignUserToTenant(assignUserRequest, tenant, user);

        // Then
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("Utente assegnato alla tenant", result.getBody().message());
        assertEquals(HttpStatus.OK.value(), result.getBody().status());
        verify(tenantService).assignUserToTenant(assignUserRequest, tenant, user);
    }

    @Test
    void testLogin_Success() {
        // Given
        when(userService.authenticateUser(loginRequest)).thenReturn(loginResponse);

        // When
        ResponseEntity<LoginResponse> result = authController.login(loginRequest);

        // Then
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(loginResponse, result.getBody());
        verify(userService).authenticateUser(loginRequest);
    }

    @Test
    void testLogin_AuthenticationFails_ReturnsUnauthorized() {
        // Given
        when(userService.authenticateUser(loginRequest))
                .thenThrow(new RuntimeException("Invalid credentials"));

        // When
        ResponseEntity<LoginResponse> result = authController.login(loginRequest);

        // Then
        assertNotNull(result);
        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertNotNull(result.getBody());
        assertNull(result.getBody().accessToken());
        assertNull(result.getBody().refreshToken());
        assertEquals("Invalid credentials", result.getBody().message());
        assertNull(result.getBody().tenantId());
        assertFalse(result.getBody().success());
        verify(userService).authenticateUser(loginRequest);
    }

    @Test
    void testLogout_Success() {
        // Given
        //when(httpRequest.getHeader("Authorization")).thenReturn("Bearer test-token");
        doNothing().when(userService).invalidateToken(httpRequest);

        // When
        ResponseEntity<ApiResponse> result = authController.logout(httpRequest);

        // Then
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("Logout effettuato con successo", result.getBody().message());
        assertEquals(HttpStatus.OK.value(), result.getBody().status());
        verify(userService).invalidateToken(httpRequest);
    }

    @Test
    void testLogout_Exception_ReturnsBadRequest() {
        // Given
        //when(httpRequest.getHeader("Authorization")).thenReturn("Bearer test-token");
        doThrow(new RuntimeException("Logout failed")).when(userService).invalidateToken(httpRequest);

        // When
        ResponseEntity<ApiResponse> result = authController.logout(httpRequest);

        // Then
        assertNotNull(result);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("Logout failed", result.getBody().message());
        assertEquals(HttpStatus.BAD_REQUEST.value(), result.getBody().status());
        verify(userService).invalidateToken(httpRequest);
    }
}

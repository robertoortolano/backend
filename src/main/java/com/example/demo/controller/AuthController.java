package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.repository.*;
import com.example.demo.security.CurrentTenant;
import com.example.demo.security.CurrentUser;
import com.example.demo.security.JwtTokenUtil;
import com.example.demo.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String MESSAGE = "Message";

    private final UserService userService;
    private final TenantService tenantService;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil; // Classe per gestire JWT
    private final TokenBlacklistRepository tokenBlacklistRepository; // (Opzionale per logout)

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> registerUser(@RequestBody RegisterRequest request) {

        userService.registerUser(request); // solo username e password
        return ResponseEntity.ok(new ApiResponse("Utente registrato con successo", HttpStatus.OK.value()));

    }

    @PostMapping("/create-tenant")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> createTenant(
            @RequestBody RegisterTenantRequest request,
            @CurrentUser User user
    ) {
        String newToken = tenantService.createTenantForCurrentUser(
                user,
                request.licenseKey(),
                request.subdomain()
        );

        return ResponseEntity.ok(Map.of(
                MESSAGE, "Tenant creato",
                "subdomain", request.subdomain(),
                "token", newToken // nuovo token con tenantId incluso
        ));
    }





    /*
    @PostMapping("/assign-user")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<?> assignUserToTenant(@RequestBody AssignUserRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        User currentAdmin = userDetails.getUser();

        try {
            tenantService.assignUserToTenant(currentAdmin, request);
            return ResponseEntity.ok(Map.of("message", "Utente assegnato alla tenant"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    */

    @PostMapping("/assign-user")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse> assignUserToTenant(
            @RequestBody AssignUserRequest request,
            @CurrentTenant Tenant tenant,
            @CurrentUser User user
            ) {
        tenantService.assignUserToTenant(request, tenant, user);
        return ResponseEntity.ok(new ApiResponse("Utente assegnato alla tenant", HttpStatus.OK.value()));
    }


    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = userService.authenticateUser(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(
                    new LoginResponse(null, null, e.getMessage(), null,false)
            );
        }
    }


    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> logout(HttpServletRequest request) {
        try {
            userService.invalidateToken(request); // Implementa questa logica
            return ResponseEntity.ok(new ApiResponse("Logout effettuato con successo", HttpStatus.OK.value()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(e.getMessage(), HttpStatus.BAD_REQUEST.value()));
        }
    }

}





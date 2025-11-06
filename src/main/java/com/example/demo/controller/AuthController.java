package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.User;
import com.example.demo.repository.*;
import com.example.demo.security.CurrentUser;
import com.example.demo.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Slf4j
@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String MESSAGE = "Message";

    private final UserService userService;
    private final TenantService tenantService;

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

    @PostMapping("/select-tenant")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> selectTenant(
            @RequestBody Map<String, Object> request,
            @CurrentUser User user
    ) {
        Object tenantIdObj = request.get("tenantId");
        
        if (tenantIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    MESSAGE, "tenantId is required"
            ));
        }
        
        Long tenantId;
        if (tenantIdObj instanceof Integer) {
            tenantId = ((Integer) tenantIdObj).longValue();
        } else if (tenantIdObj instanceof Long) {
            tenantId = (Long) tenantIdObj;
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    MESSAGE, "Invalid tenantId format"
            ));
        }
        
        String newToken = tenantService.selectTenant(user, tenantId);
        return ResponseEntity.ok(Map.of(
                MESSAGE, "Tenant selezionato",
                "token", newToken
        ));
    }






    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = userService.authenticateUser(request);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> logout(HttpServletRequest request) {
        userService.invalidateToken(request); // Implementa questa logica
        return ResponseEntity.ok(new ApiResponse("Logout effettuato con successo", HttpStatus.OK.value()));
    }

}





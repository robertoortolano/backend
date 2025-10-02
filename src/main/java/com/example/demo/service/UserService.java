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
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    private final TenantService tenantService;

    private final JwtTokenUtil jwtTokenUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;


    public void registerUser(RegisterRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new ApiException("Username già in uso");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public LoginResponse authenticateUser(LoginRequest request) {
        // 1. Autenticazione utente
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );

        // 2. Set nel SecurityContext
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. Estrai dettagli utente
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        // 4. Trova tenant associato
        Tenant tenant = tenantService.getFirstByAdminUser(request.username());
        Long tenantId = tenant != null ? tenant.getId() : null;

        // 5. Imposta tenant attivo e salva
        user.setActiveTenant(tenant);
        userRepository.save(user);

        // 6. Genera token JWT
        String accessToken = (tenantId != null)
                ? jwtTokenUtil.generateAccessTokenWithTenantId(userDetails, tenantId)
                : jwtTokenUtil.generateAccessTokenWithoutTenantId(userDetails);

        String refreshToken = jwtTokenUtil.generateRefreshToken(userDetails);

        // 7. Ritorna risposta
        return new LoginResponse(accessToken, refreshToken, "Login success", tenantId, true);
    }


    public void invalidateToken(HttpServletRequest request) {
        // Aggiungi il token a una blacklist (Redis o DB)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            tokenBlacklistRepository.save(new InvalidatedToken(token));
            SecurityContextHolder.clearContext();
        }
    }

    @Transactional(readOnly = true)
    public Tenant findFirstTenantByUsername(String username) {
        return tenantService.getFirstByAdminUser(username);
    }

    @Transactional(readOnly = true)
    public Tenant getActiveTenantForUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getActiveTenant() == null) {
            throw new ApiException("No active tenant set");
        }
        return user.getActiveTenant();
    }

}






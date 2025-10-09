package com.example.demo.service;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.User;
import com.example.demo.repository.TokenBlacklistRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.security.CustomUserDetailsService;
import com.example.demo.security.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService customUserDetailsService;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Transactional
    public void registerUser(RegisterRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);
    }

    @Transactional
    public LoginResponse authenticateUser(LoginRequest request) {
        try {
            System.out.println("=== Attempting authentication for user: " + request.username());
            
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
            System.out.println("=== Authentication successful");

            CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername(request.username());
            System.out.println("=== User details loaded");
            
            String accessToken = jwtTokenUtil.generateAccessTokenWithoutTenantId(userDetails);
            System.out.println("=== Access token generated");
            
            String refreshToken = jwtTokenUtil.generateRefreshToken(userDetails);
            System.out.println("=== Refresh token generated");

            return new LoginResponse(accessToken, refreshToken, "Login successful", null, true);
        } catch (Exception e) {
            System.err.println("=== Authentication error: " + e.getClass().getName());
            System.err.println("=== Error message: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Invalid credentials: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void invalidateToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // Add token to blacklist logic here if needed
            // For now, just validate the token exists
            if (token.isEmpty()) {
                throw new RuntimeException("Invalid token");
            }
        }
    }

    @Transactional
    public User createUser(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public void addTenantToUser(Long userId, Long tenantId) {
        // Implementa la logica per aggiungere un tenant all'utente
    }
}

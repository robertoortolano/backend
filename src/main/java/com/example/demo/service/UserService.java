package com.example.demo.service;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.User;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.TokenBlacklistRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.security.CustomUserDetailsService;
import com.example.demo.security.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
            throw new ApiException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);
    }

    @Transactional
    public LoginResponse authenticateUser(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername(request.username());
            
            String accessToken = jwtTokenUtil.generateAccessTokenWithoutTenantId(userDetails);
            
            String refreshToken = jwtTokenUtil.generateRefreshToken(userDetails);

            return new LoginResponse(accessToken, refreshToken, "Login successful", null, true);
        } catch (Exception e) {
            log.error("Authentication error: {} - {}", e.getClass().getName(), e.getMessage(), e);
            throw new ApiException("Invalid credentials: " + e.getMessage());
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
                throw new ApiException("Invalid token");
            }
        }
    }

    @Transactional
    public User createUser(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ApiException("Username already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException("User not found"));
    }

    @Transactional
    public void addTenantToUser(Long userId, Long tenantId) {
        // Implementa la logica per aggiungere un tenant all'utente
    }
}

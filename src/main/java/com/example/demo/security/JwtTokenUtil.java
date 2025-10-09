package com.example.demo.security;


import com.example.demo.entity.GrantRoleAssignment;
import com.example.demo.enums.ScopeType;
import com.example.demo.entity.User;
import com.example.demo.repository.GrantRoleAssignmentRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.*;
import java.util.function.Function;

@Component
public class JwtTokenUtil {

    private final GrantRoleAssignmentRepository grantRoleAssignmentRepository;

    private static final String ROLES = "roles";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    public JwtTokenUtil(GrantRoleAssignmentRepository grantRoleAssignmentRepository) {
        this.grantRoleAssignmentRepository = grantRoleAssignmentRepository;
    }


    public String generateAccessTokenWithoutTenantId(CustomUserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        User user = userDetails.getUser();

        claims.put("userId", user.getId());

        claims.put(ROLES, List.of());

        return buildToken(claims, userDetails.getUsername(), accessTokenExpirationMs);
    }




    // Genera un token di accesso
    public String generateAccessTokenWithTenantId(CustomUserDetails userDetails, Long tenantId) {
        Map<String, Object> claims = new HashMap<>();
        User user = userDetails.getUser();

        claims.put("userId", user.getId());
        claims.put("tenantId", tenantId);

        // 🔍 Ottieni i ruoli TENANT dell'utente per quella tenant
        List<Map<String, String>> roles = grantRoleAssignmentRepository.findAllByUserAndTenant(user.getId(), tenantId).stream()
                .map(GrantRoleAssignment::getRole)
                .filter(role -> role.getScope() == ScopeType.TENANT)
                .map(role -> Map.of("name", role.getName(), "scope", role.getScope().name()))
                .toList();

        claims.put(ROLES, roles);

        return buildToken(claims, userDetails.getUsername(), accessTokenExpirationMs);
    }


    // Genera un refresh token
    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails.getUsername(), refreshTokenExpirationMs);
    }

    private String buildToken(Map<String, Object> claims, String subject, long expirationMs) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Estrae l'username dal token
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Estrae la data di scadenza
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Verifica se il token è scaduto
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // Validazione del token
    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    // Metodo generico per estrarre claims
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        byte[] keyBytes = secret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public List<String> extractRoles(String token) {
        Claims claims = extractAllClaims(token);
        Object rolesObject = claims.get(ROLES);
        if (rolesObject instanceof List<?>) {
            return ((List<?>) rolesObject).stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    public Long extractTenantId(String token) {
        Claims claims = extractAllClaims(token);
        Object tenantIdObj = claims.get("tenantId");

        if (tenantIdObj == null) {
            return null;  // Nessun tenantId nel token
        }

        return switch (tenantIdObj) {
            case Long tenantId -> tenantId;
            case Integer tenantId -> tenantId.longValue();
            case String tenantId -> {
                try {
                    yield Long.parseLong(tenantId);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null; // Tipo non gestito
        };
    }





}
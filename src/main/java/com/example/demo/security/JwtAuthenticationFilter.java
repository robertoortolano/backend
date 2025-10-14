package com.example.demo.security;

import com.example.demo.repository.TokenBlacklistRepository;
import com.example.demo.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            final String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            final String token = authHeader.substring(7);

            // Verifica se il token è nella blacklist
            if (tokenBlacklistRepository.existsByToken(token)) {
                log.warn("Tentativo di accesso con token revocato");
                sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "Token revocato");
                return;
            }

            // Verifica la scadenza del token
            if (jwtTokenUtil.isTokenExpired(token)) {
                log.warn("Tentativo di accesso con token scaduto");
                sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "Token scaduto");
                return;
            }

            // Estrai username e verifica il token
            final String username = jwtTokenUtil.extractUsername(token);
            if (username == null) {
                log.warn("Token JWT non valido: username mancante");
                sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "Token non valido");
                return;
            }

            // Carica i dettagli dell'utente solo se necessario
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                // IMPORTANTE: Estrai il tenantId dal token PRIMA di caricare l'utente
                Long tenantId = jwtTokenUtil.extractTenantId(token);
                
                // Imposta il tenant nel contesto PRIMA di caricare UserDetails
                if (tenantId != null) {
                    log.debug("Impostato tenant ID: {}", tenantId);
                    TenantContext.setCurrentTenantId(tenantId);
                } else {
                    log.debug("Nessun tenant ID trovato nel token");
                    TenantContext.clear();
                }
                
                // Ora carica i dettagli dell'utente (che userà il tenantId dal contesto)
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                
                if (jwtTokenUtil.validateToken(token, userDetails)) {
                    // Crea il contesto di autenticazione
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    // Imposta il contesto di sicurezza
                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authToken);
                    SecurityContextHolder.setContext(context);
                } else {
                    log.warn("Validazione del token fallita per l'utente: {}", username);
                    sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "Token non valido");
                    return;
                }
            }

            filterChain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Errore durante l'autenticazione", e);
            sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, "Errore di autenticazione");
        } finally {
            // Pulisci sempre il contesto del tenant per evitare memory leak
            TenantContext.clear();
        }
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}",
                status.value(), status.getReasonPhrase(), message));
    }
}




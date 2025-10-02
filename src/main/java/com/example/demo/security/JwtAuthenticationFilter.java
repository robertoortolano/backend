package com.example.demo.security;

import com.example.demo.repository.TokenBlacklistRepository;
import com.example.demo.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        try {

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            final String token = authHeader.substring(7);

            if (tokenBlacklistRepository.existsByToken(token)) {
                logger.warn("Token nella blacklist: accesso negato");
                filterChain.doFilter(request, response);
                return;
            }

            final String username = jwtTokenUtil.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                if (jwtTokenUtil.isTokenExpired(token)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                CustomUserDetails customUserDetails =
                        (CustomUserDetails) userDetailsService.loadUserByUsername(username);

                if (jwtTokenUtil.validateToken(token, customUserDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    Long tenantId = jwtTokenUtil.extractTenantId(token);
                    if (tenantId != null) {
                        TenantContext.setCurrentTenantId(tenantId);
                    } else {
                        // TenantId mancante: probabilmente è la richiesta di creazione tenant
                        TenantContext.clear(); // oppure lascialo nullo, a seconda della logica downstream
                    }
                }
            }
            logger.info("Auth set: " + SecurityContextHolder.getContext().getAuthentication());
            filterChain.doFilter(request, response);
        } finally {
            // ✅ Pulisci sempre il contesto, per evitare memory leak
            TenantContext.clear();
        }
    }
}




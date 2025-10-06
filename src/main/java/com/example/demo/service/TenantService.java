package com.example.demo.service;

import com.example.demo.dto.AssignUserRequest;
import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.initializer.TenantInitializer;
import com.example.demo.repository.*;
import com.example.demo.security.CustomUserDetails;
import com.example.demo.security.CustomUserDetailsService;
import com.example.demo.security.JwtTokenUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;

    private final UserRepository userRepository;
    private final LicenseRepository licenseRepository;
    private final RoleRepository roleRepository;
    private final GrantRepository grantRepository;
    private final GrantRoleAssignmentRepository grantRoleAssignmentRepository;

    private final CustomUserDetailsService customUserDetailsService;
    private final LicenseService licenseService;

    private final GrantRoleLookup grantRoleLookup;

    private final List<TenantInitializer> tenantInitializers;
    private final JwtTokenUtil jwtTokenUtil;

    @Value("${app.domain}")
    private String baseDomain;


    @Transactional
    public String createTenantForCurrentUser(User user, String licenseKey, String subdomain) {

        // ✅ Validazioni iniziali
        if (licenseService.exists(licenseKey)) {
            throw new ApiException("Licenza già utilizzata");
        }

        if (!licenseService.isValidLicenseKey(licenseKey)) {
            throw new ApiException("Licenza non valida");
        }

        if (tenantRepository.existsBySubdomain(subdomain)) {
            throw new ApiException("Dominio già utilizzato");
        }

        // ✅ Crea licenza e tenant
        License license = new License();
        license.setLicenseKey(licenseKey);

        Tenant tenant = new Tenant();
        tenant.setSubdomain(subdomain);
        tenant.setLicense(license);
        tenant.setTenantAdmin(user);
        license.setTenant(tenant); // bidirezionale

        licenseRepository.save(license);
        Tenant persistedTenant = license.getTenant();

        // ✅ Imposta tenant attivo all'utente
        user.setActiveTenant(persistedTenant);

        // ✅ Crea Grant iniziale per il tenant admin
        Grant adminGrant = new Grant();
        adminGrant.setUsers(Set.of(user)); // solo l'utente creatore

        // ✅ Crea il ruolo TENANT_ADMIN
        Role tenantAdminRole = new Role();
        tenantAdminRole.setName("ADMIN");
        tenantAdminRole.setScope(ScopeType.TENANT);
        tenantAdminRole.setDefaultRole(true);
        tenantAdminRole.setTenant(persistedTenant);
        roleRepository.save(tenantAdminRole);

        // ✅ Associa il ruolo alla grant
        GrantRoleAssignment assignment = new GrantRoleAssignment();
        assignment.setGrant(adminGrant);
        assignment.setRole(tenantAdminRole);
        assignment.setTenant(persistedTenant);

        // ✅ Persisti il tenant (con cascade sulla licenza)
        licenseRepository.save(license);

        // ✅ Salva grant e associazione ruolo
        grantRepository.save(adminGrant);
        grantRoleAssignmentRepository.save(assignment);

        userRepository.save(user); // aggiorna activeTenant

        // ✅ Inizializza il contenuto della tenant (workflow, ruoli, status...)
        tenantInitializers.forEach(initializer -> initializer.initialize(persistedTenant));

        // ✅ Ricarica UserDetails aggiornato
        CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername(user.getUsername());

        // ✅ Genera token CON tenantId
        return jwtTokenUtil.generateAccessTokenWithTenantId(userDetails, persistedTenant.getId());
    }





    public void assignUserToTenant(AssignUserRequest request, Tenant tenant, User adminUser) {
        // Controlla se adminUser è admin tenant (con Grant e Role)
        boolean isAdmin = grantRoleLookup.getAllByUser(adminUser, tenant).stream()
                .anyMatch(gra -> "ADMIN".equals(gra.getRole().getName()) && gra.getRole().getScope().equals(ScopeType.TENANT));

        if (!isAdmin) {
            throw new ApiException("Non autorizzato");
        }

        Role role = grantRoleLookup.getRoleByNameAndScope(request.role(), ScopeType.TENANT, tenant);

        // Verifica se l’utente è già assegnato a questo ruolo nella tenant
        boolean alreadyAssigned = grantRoleLookup.existsByUserGlobal(adminUser, tenant, role.getName());

        if (alreadyAssigned) {
            throw new ApiException("Utente già assegnato a questo ruolo nella tenant");
        }

        // Cerca un Grant esistente per quel ruolo e tenant oppure creane uno nuovo
        GrantRoleAssignment gra = grantRoleLookup.getByRoleAndScope(role,ScopeType.TENANT, tenant);

        Grant grant = gra.getGrant();
         if (grant == null) {
             grant = new Grant();
             gra.setGrant(grant);
             grant.setRole(role);
         }

        // Aggiungi l’utente al grant
        grant.getUsers().add(adminUser);

        // Salva il grant
        grantRepository.save(grant);
    }

    public Tenant getFirstByAdminUser(String username) {
        return tenantRepository.findFirstByTenantAdminUsername(username).orElse(null);
    }


}

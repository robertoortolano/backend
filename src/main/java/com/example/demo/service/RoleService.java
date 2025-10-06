package com.example.demo.service;

import com.example.demo.dto.RoleCreateDto;
import com.example.demo.dto.RoleUpdateDto;
import com.example.demo.dto.RoleViewDto;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.enums.ScopeType;
import com.example.demo.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;

    /**
     * Crea un nuovo ruolo per il tenant specificato.
     * Questo metodo è specifico per la gestione UI e forza scope TENANT e defaultRole false.
     */
    public RoleViewDto createTenantRole(RoleCreateDto createDto, Tenant tenant) {
        // Verifica che non esista già un ruolo con lo stesso nome per questo tenant
        if (roleRepository.existsByNameAndTenant(createDto.name(), tenant)) {
            throw new IllegalArgumentException("Un ruolo con il nome '" + createDto.name() + "' esiste già per questo tenant");
        }

        Role role = new Role();
        role.setName(createDto.name());
        role.setDescription(createDto.description());
        role.setScope(ScopeType.TENANT); // Forzato per la gestione UI
        role.setDefaultRole(false); // Forzato per la gestione UI
        role.setTenant(tenant);

        Role savedRole = roleRepository.save(role);
        return toViewDto(savedRole);
    }

    /**
     * Aggiorna un ruolo tenant esistente.
     * Questo metodo è specifico per la gestione UI e gestisce solo ruoli tenant non di default.
     */
    public RoleViewDto updateTenantRole(Long roleId, RoleUpdateDto updateDto, Tenant tenant) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Ruolo non trovato con ID: " + roleId));

        // Verifica che il ruolo appartenga al tenant
        if (!role.getTenant().getId().equals(tenant.getId())) {
            throw new IllegalArgumentException("Il ruolo non appartiene a questo tenant");
        }

        // Verifica che sia un ruolo tenant (gestito dalla UI)
        if (role.getScope() != ScopeType.TENANT) {
            throw new IllegalArgumentException("Questo ruolo non può essere modificato tramite la gestione UI");
        }

        // Verifica che non esista già un altro ruolo con lo stesso nome per questo tenant
        if (!role.getName().equals(updateDto.name()) && 
            roleRepository.existsByNameAndTenant(updateDto.name(), tenant)) {
            throw new IllegalArgumentException("Un ruolo con il nome '" + updateDto.name() + "' esiste già per questo tenant");
        }

        role.setName(updateDto.name());
        role.setDescription(updateDto.description());
        // Manteniamo scope TENANT e defaultRole false per la gestione UI

        Role savedRole = roleRepository.save(role);
        return toViewDto(savedRole);
    }

    /**
     * Elimina un ruolo tenant.
     * Questo metodo è specifico per la gestione UI e gestisce solo ruoli tenant non di default.
     */
    public void deleteTenantRole(Long roleId, Tenant tenant) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Ruolo non trovato con ID: " + roleId));

        // Verifica che il ruolo appartenga al tenant
        if (!role.getTenant().getId().equals(tenant.getId())) {
            throw new IllegalArgumentException("Il ruolo non appartiene a questo tenant");
        }

        // Verifica che sia un ruolo tenant (gestito dalla UI)
        if (role.getScope() != ScopeType.TENANT) {
            throw new IllegalArgumentException("Questo ruolo non può essere eliminato tramite la gestione UI");
        }

        // Verifica che non sia un ruolo di default
        if (role.isDefaultRole()) {
            throw new IllegalArgumentException("Non è possibile eliminare un ruolo di default");
        }

        roleRepository.delete(role);
    }

    /**
     * Ottiene tutti i ruoli tenant non di default per il tenant specificato.
     * Questo metodo è specifico per la gestione UI.
     */
    @Transactional(readOnly = true)
    public List<RoleViewDto> getAllTenantRoles(Tenant tenant) {
        List<Role> roles = roleRepository.findByScopeAndTenantId(ScopeType.TENANT, tenant.getId());
        return roles.stream()
                .filter(role -> !role.isDefaultRole()) // Solo ruoli non di default per la gestione UI
                .map(this::toViewDto)
                .collect(Collectors.toList());
    }

    /**
     * Ottiene un ruolo tenant specifico per ID.
     * Questo metodo è specifico per la gestione UI.
     */
    @Transactional(readOnly = true)
    public RoleViewDto getTenantRoleById(Long roleId, Tenant tenant) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Ruolo non trovato con ID: " + roleId));

        // Verifica che il ruolo appartenga al tenant
        if (!role.getTenant().getId().equals(tenant.getId())) {
            throw new IllegalArgumentException("Il ruolo non appartiene a questo tenant");
        }

        // Verifica che sia un ruolo tenant (gestito dalla UI)
        if (role.getScope() != ScopeType.TENANT) {
            throw new IllegalArgumentException("Questo ruolo non può essere visualizzato tramite la gestione UI");
        }

        return toViewDto(role);
    }

    /**
     * Converte un'entità Role in RoleViewDto.
     */
    private RoleViewDto toViewDto(Role role) {
        return new RoleViewDto(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getScope(),
                role.isDefaultRole()
        );
    }
}

package com.example.demo.service;

import com.example.demo.dto.RoleCreateDto;
import com.example.demo.dto.RoleUpdateDto;
import com.example.demo.dto.RoleViewDto;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
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
     * Crea un nuovo ruolo custom per il tenant specificato.
     * Nota: I Role custom sono sempre a livello TENANT implicitamente (nessun campo scope).
     * Questo è diverso da UserRole che gestisce ADMIN/USER per l'autenticazione.
     */
    public RoleViewDto createTenantRole(RoleCreateDto createDto, Tenant tenant) {
        // Verifica che non esista già un ruolo con lo stesso nome per questo tenant
        if (roleRepository.existsByNameAndTenant(createDto.name(), tenant)) {
            throw new IllegalArgumentException("Un ruolo con il nome '" + createDto.name() + "' esiste già per questo tenant");
        }

        Role role = new Role();
        role.setName(createDto.name());
        role.setDescription(createDto.description());
        role.setDefaultRole(false); // I ruoli custom non sono mai di default
        role.setTenant(tenant);

        Role savedRole = roleRepository.save(role);
        return toViewDto(savedRole);
    }

    /**
     * Aggiorna un ruolo custom esistente.
     * Gestisce solo ruoli custom (non di default) del tenant.
     */
    public RoleViewDto updateTenantRole(Long roleId, RoleUpdateDto updateDto, Tenant tenant) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Ruolo non trovato con ID: " + roleId));

        // Verifica che il ruolo appartenga al tenant
        if (!role.getTenant().getId().equals(tenant.getId())) {
            throw new IllegalArgumentException("Il ruolo non appartiene a questo tenant");
        }

        // Verifica che non sia un ruolo di default (non modificabile dalla UI)
        if (role.isDefaultRole()) {
            throw new IllegalArgumentException("I ruoli di default non possono essere modificati");
        }

        // Verifica che non esista già un altro ruolo con lo stesso nome per questo tenant
        if (!role.getName().equals(updateDto.name()) && 
            roleRepository.existsByNameAndTenant(updateDto.name(), tenant)) {
            throw new IllegalArgumentException("Un ruolo con il nome '" + updateDto.name() + "' esiste già per questo tenant");
        }

        role.setName(updateDto.name());
        role.setDescription(updateDto.description());

        Role savedRole = roleRepository.save(role);
        return toViewDto(savedRole);
    }

    /**
     * Elimina un ruolo custom.
     * Gestisce solo ruoli custom (non di default) del tenant.
     */
    public void deleteTenantRole(Long roleId, Tenant tenant) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Ruolo non trovato con ID: " + roleId));

        // Verifica che il ruolo appartenga al tenant
        if (!role.getTenant().getId().equals(tenant.getId())) {
            throw new IllegalArgumentException("Il ruolo non appartiene a questo tenant");
        }

        // Verifica che non sia un ruolo di default
        if (role.isDefaultRole()) {
            throw new IllegalArgumentException("Non è possibile eliminare un ruolo di default");
        }

        roleRepository.delete(role);
    }

    /**
     * Ottiene tutti i ruoli custom (non di default) per il tenant specificato.
     * Usato dalla gestione UI (/tenant/roles).
     */
    @Transactional(readOnly = true)
    public List<RoleViewDto> getAllTenantRoles(Tenant tenant) {
        List<Role> roles = roleRepository.findByTenantId(tenant.getId());
        return roles.stream()
                .filter(role -> !role.isDefaultRole()) // Solo ruoli custom (non di default)
                .map(this::toViewDto)
                .collect(Collectors.toList());
    }

    /**
     * Ottiene un ruolo custom specifico per ID.
     * Usato dalla gestione UI (/tenant/roles).
     */
    @Transactional(readOnly = true)
    public RoleViewDto getTenantRoleById(Long roleId, Tenant tenant) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Ruolo non trovato con ID: " + roleId));

        // Verifica che il ruolo appartenga al tenant
        if (!role.getTenant().getId().equals(tenant.getId())) {
            throw new IllegalArgumentException("Il ruolo non appartiene a questo tenant");
        }

        return toViewDto(role);
    }

    /**
     * Converte un'entità Role in RoleViewDto.
     * Nota: Role non ha più campo scope (i Role custom sono sempre TENANT implicitamente).
     */
    private RoleViewDto toViewDto(Role role) {
        return new RoleViewDto(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.isDefaultRole()
        );
    }
}

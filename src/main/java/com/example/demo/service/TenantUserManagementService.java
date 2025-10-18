package com.example.demo.service;

import com.example.demo.dto.TenantUserDto;
import com.example.demo.dto.UserAccessStatusDto;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.entity.UserRole;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TenantUserManagementService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final DtoMapperFacade dtoMapper;

    /**
     * Assegna un ruolo (ADMIN o USER) a un utente in una tenant
     */
    public void assignRole(String username, String roleName, Tenant tenant, User currentUser) {
        // Validazione roleName
        if (!roleName.equals("ADMIN") && !roleName.equals("USER")) {
            throw new ApiException("Role name must be ADMIN or USER");
        }

        // Verifica che il current user sia ADMIN della tenant
        if (!userRoleRepository.existsByUserIdAndTenantIdAndRoleName(
                currentUser.getId(), tenant.getId(), "ADMIN")) {
            throw new ApiException("Only ADMIN can assign roles to users");
        }

        // Trova l'utente da aggiungere (username è email)
        User userToGrant = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException("User with email '" + username + "' not found"));

        // Verifica se l'utente ha già accesso
        if (userRoleRepository.hasAccessToTenant(userToGrant.getId(), tenant.getId())) {
            throw new ApiException("User already has access to this tenant");
        }

        // Crea UserRole con il ruolo specificato
        UserRole newUserRole = UserRole.builder()
                .user(userToGrant)
                .tenant(tenant)
                .roleName(roleName)
                .scope(ScopeType.TENANT)
                .project(null)
                .build();

        userRoleRepository.save(newUserRole);
    }
    
    /**
     * Backward compatibility - concede accesso USER
     */
    public void grantUserAccess(String username, Tenant tenant, User currentUser) {
        assignRole(username, "USER", tenant, currentUser);
    }

    /**
     * Aggiorna i ruoli di un utente esistente nella tenant.
     * Nota: ADMIN e USER sono mutualmente esclusivi - un utente può avere solo uno dei due.
     */
    public void updateUserRoles(Long userId, List<String> newRoleNames, Tenant tenant, User currentUser) {
        // Validazione
        if (newRoleNames == null || newRoleNames.isEmpty()) {
            throw new ApiException("At least one role must be assigned");
        }

        // Validazione: ADMIN e USER sono mutualmente esclusivi
        if (newRoleNames.contains("ADMIN") && newRoleNames.contains("USER")) {
            throw new ApiException("ADMIN and USER roles are mutually exclusive. A user can have only one of them.");
        }

        // Deve avere esattamente un ruolo (ADMIN o USER, non entrambi)
        if (newRoleNames.size() != 1) {
            throw new ApiException("A user must have exactly one role: either ADMIN or USER");
        }

        // Valida che tutti i ruoli siano validi
        for (String roleName : newRoleNames) {
            if (!roleName.equals("ADMIN") && !roleName.equals("USER")) {
                throw new ApiException("Invalid role name: " + roleName + ". Must be ADMIN or USER");
            }
        }

        // Verifica che il current user sia ADMIN della tenant
        if (!userRoleRepository.existsByUserIdAndTenantIdAndRoleName(
                currentUser.getId(), tenant.getId(), "ADMIN")) {
            throw new ApiException("Only ADMIN can change user roles");
        }

        // Trova l'utente
        User userToChange = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found"));

        // Verifica che l'utente abbia accesso
        if (!userRoleRepository.hasAccessToTenant(userId, tenant.getId())) {
            throw new ApiException("User doesn't have access to this tenant");
        }

        // Ottieni i ruoli attuali
        List<UserRole> currentRoles = userRoleRepository.findTenantRolesByUserAndTenant(userId, tenant.getId());
        boolean currentlyHasAdmin = currentRoles.stream()
                .anyMatch(ur -> ur.getRoleName().equals("ADMIN"));
        
        boolean willHaveAdmin = newRoleNames.contains("ADMIN");

        // Se l'utente HA ADMIN attualmente ma NON lo avrà più, verifica che non sia l'ultimo
        if (currentlyHasAdmin && !willHaveAdmin) {
            long adminCount = userRoleRepository.countByTenantIdAndRoleName(tenant.getId(), "ADMIN");
            if (adminCount <= 1) {
                throw new ApiException("Cannot remove ADMIN role from the last ADMIN of the tenant");
            }
        }

        // Rimuovi tutti i ruoli TENANT attuali
        userRoleRepository.deleteByUserIdAndTenantIdAndScope(userId, tenant.getId());

        // Crea i nuovi ruoli
        for (String roleName : newRoleNames) {
            UserRole newUserRole = UserRole.builder()
                    .user(userToChange)
                    .tenant(tenant)
                    .roleName(roleName)
                    .scope(ScopeType.TENANT)
                    .project(null)
                    .build();
            userRoleRepository.save(newUserRole);
        }
    }
    
    /**
     * @deprecated Usa updateUserRoles() per maggiore flessibilità.
     * Cambia il ruolo di un utente esistente (da USER ad ADMIN o viceversa)
     */
    @Deprecated
    public void changeUserRole(Long userId, String newRoleName, Tenant tenant, User currentUser) {
        updateUserRoles(userId, List.of(newRoleName), tenant, currentUser);
    }

    /**
     * Revoca completamente l'accesso di un utente da una tenant
     */
    public void revokeUserAccess(Long userIdToRevoke, Tenant tenant, User currentUser) {
        // Verifica che il current user sia ADMIN della tenant
        if (!userRoleRepository.existsByUserIdAndTenantIdAndRoleName(
                currentUser.getId(), tenant.getId(), "ADMIN")) {
            throw new ApiException("Only ADMIN can revoke access from users");
        }

        // Trova l'utente da rimuovere
        User userToRevoke = userRepository.findById(userIdToRevoke)
                .orElseThrow(() -> new ApiException("User not found"));

        // Verifica che l'utente abbia accesso
        if (!userRoleRepository.hasAccessToTenant(userToRevoke.getId(), tenant.getId())) {
            throw new ApiException("User doesn't have access to this tenant");
        }

        // Verifica che l'utente da rimuovere non sia l'ultimo ADMIN
        boolean isAdmin = userRoleRepository.existsByUserIdAndTenantIdAndRoleName(
                userToRevoke.getId(), tenant.getId(), "ADMIN");

        if (isAdmin) {
            long adminCount = userRoleRepository.countByTenantIdAndRoleName(tenant.getId(), "ADMIN");
            if (adminCount <= 1) {
                throw new ApiException("Cannot remove the last ADMIN of the tenant");
            }
        }

        // Rimuovi tutti i UserRole dell'utente per questa tenant (scope TENANT)
        userRoleRepository.deleteByUserIdAndTenantIdAndScope(userToRevoke.getId(), tenant.getId());
    }

    /**
     * Ottiene lo stato di accesso di un utente specifico
     */
    @Transactional(readOnly = true)
    public UserAccessStatusDto getUserAccessStatus(String username, Tenant tenant) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException("User with username '" + username + "' not found"));

        boolean hasAccess = userRoleRepository.hasAccessToTenant(user.getId(), tenant.getId());

        List<String> roles = List.of();
        if (hasAccess) {
            roles = userRoleRepository.findTenantRolesByUserAndTenant(user.getId(), tenant.getId())
                    .stream()
                    .map(UserRole::getRoleName)
                    .distinct()
                    .collect(Collectors.toList());
        }

        return new UserAccessStatusDto(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                hasAccess,
                roles
        );
    }

    /**
     * Ottiene tutti gli utenti con accesso alla tenant
     */
    @Transactional(readOnly = true)
    public List<TenantUserDto> getAllUsersWithAccess(Tenant tenant) {
        List<User> users = userRoleRepository.findUsersByTenantId(tenant.getId());

        return users.stream()
                .map(user -> {
                    // Ottieni ruoli tenant
                    List<String> roles = userRoleRepository.findTenantRolesByUserAndTenant(user.getId(), tenant.getId())
                            .stream()
                            .map(UserRole::getRoleName)
                            .distinct()
                            .collect(Collectors.toList());

                    // Ottieni progetti per cui l'utente è PROJECT_ADMIN
                    List<com.example.demo.dto.ProjectSummaryDto> projectAdminOf = 
                            userRoleRepository.findProjectsByUserIdAndTenantIdAndRoleName(
                                    user.getId(), tenant.getId(), "ADMIN")
                            .stream()
                            .map(dtoMapper::toProjectSummaryDto)
                            .collect(Collectors.toList());

                    return new TenantUserDto(
                            user.getId(),
                            user.getUsername(),
                            user.getFullName(),
                            roles,
                            projectAdminOf
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Verifica se un utente può essere rimosso (non è l'ultimo ADMIN)
     */
    @Transactional(readOnly = true)
    public boolean canRevokeAccess(Long userId, Tenant tenant) {
        if (!userRoleRepository.hasAccessToTenant(userId, tenant.getId())) {
            return false;
        }

        boolean isAdmin = userRoleRepository.existsByUserIdAndTenantIdAndRoleName(
                userId, tenant.getId(), "ADMIN");

        if (isAdmin) {
            long adminCount = userRoleRepository.countByTenantIdAndRoleName(tenant.getId(), "ADMIN");
            return adminCount > 1;
        }

        return true;
    }
}


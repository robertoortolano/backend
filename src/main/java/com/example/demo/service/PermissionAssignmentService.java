package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Servizio per gestire PermissionAssignment (assegnazioni globali di ruoli e grant a Permission).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PermissionAssignmentService {
    
    private final PermissionAssignmentRepository permissionAssignmentRepository;
    private final RoleRepository roleRepository;
    private final GrantRepository grantRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GrantCleanupService grantCleanupService;
    
    /**
     * Crea o aggiorna un PermissionAssignment per una Permission.
     * On-demand: crea PermissionAssignment solo se necessario (ruoli o grant).
     * 
     * @param permissionType Tipo della Permission (es. "FieldOwnerPermission")
     * @param permissionId ID della Permission
     * @param roleIds Set di ID dei ruoli custom da assegnare (può essere null/empty)
     * @param grantId ID del Grant da assegnare (può essere null)
     * @param tenant Tenant di appartenenza
     * @return PermissionAssignment creato o aggiornato
     */
    public PermissionAssignment createOrUpdateAssignment(
            String permissionType,
            Long permissionId,
            Set<Long> roleIds,
            Long grantId,
            Tenant tenant) {
        
        // Trova o crea PermissionAssignment
        Optional<PermissionAssignment> existingOpt = permissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndTenant(permissionType, permissionId, tenant);
        
        PermissionAssignment assignment;
        if (existingOpt.isPresent()) {
            assignment = existingOpt.get();
        } else {
            assignment = PermissionAssignment.builder()
                    .permissionType(permissionType)
                    .permissionId(permissionId)
                    .tenant(tenant)
                    .roles(new HashSet<>())
                    .build();
        }
        
        // Aggiorna ruoli
        if (roleIds != null) {
            Set<Role> roles = new HashSet<>();
            for (Long roleId : roleIds) {
                Role role = roleRepository.findById(roleId)
                        .orElseThrow(() -> new ApiException("Role not found: " + roleId));
                // Verifica che il ruolo appartenga al tenant
                if (!role.getTenant().getId().equals(tenant.getId())) {
                    throw new ApiException("Role does not belong to tenant");
                }
                roles.add(role);
            }
            assignment.setRoles(roles);
        }
        
        // Aggiorna grant
        if (grantId != null) {
            Grant grant = grantRepository.findById(grantId)
                    .orElseThrow(() -> new ApiException("Grant not found: " + grantId));
            assignment.setGrant(grant);
        } else {
            // Se grantId è null, rimuovi il grant esistente
            if (assignment.getGrant() != null) {
                assignment.setGrant(null);
                // Non eliminiamo il Grant qui, potrebbe essere usato altrove
            }
        }
        
        return permissionAssignmentRepository.save(assignment);
    }
    
    /**
     * Ottiene PermissionAssignment per una Permission.
     * 
     * @param permissionType Tipo della Permission
     * @param permissionId ID della Permission
     * @param tenant Tenant di appartenenza
     * @return PermissionAssignment se esiste, altrimenti Optional.empty()
     */
    @Transactional(readOnly = true)
    public Optional<PermissionAssignment> getAssignment(
            String permissionType,
            Long permissionId,
            Tenant tenant) {
        return permissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndTenantWithCollections(permissionType, permissionId, tenant);
    }
    
    /**
     * Elimina PermissionAssignment per una Permission.
     * Elimina anche il Grant associato se esiste.
     * 
     * @param permissionType Tipo della Permission
     * @param permissionId ID della Permission
     * @param tenant Tenant di appartenenza
     */
    public void deleteAssignment(String permissionType, Long permissionId, Tenant tenant) {
        Optional<PermissionAssignment> assignmentOpt = permissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndTenant(permissionType, permissionId, tenant);
        
        if (assignmentOpt.isPresent()) {
            PermissionAssignment assignment = assignmentOpt.get();
            
            // Elimina Grant se esiste
            if (assignment.getGrant() != null) {
                Long grantId = assignment.getGrant().getId();
                grantCleanupService.deleteGrantCompletely(grantId);
            }
            
            // Elimina PermissionAssignment
            permissionAssignmentRepository.delete(assignment);
        }
    }
    
    /**
     * Aggiunge un ruolo a PermissionAssignment esistente.
     * 
     * @param permissionType Tipo della Permission
     * @param permissionId ID della Permission
     * @param roleId ID del ruolo da aggiungere
     * @param tenant Tenant di appartenenza
     * @return PermissionAssignment aggiornato
     */
    public PermissionAssignment addRole(
            String permissionType,
            Long permissionId,
            Long roleId,
            Tenant tenant) {
        
        PermissionAssignment assignment = permissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndTenant(permissionType, permissionId, tenant)
                .orElseGet(() -> PermissionAssignment.builder()
                        .permissionType(permissionType)
                        .permissionId(permissionId)
                        .tenant(tenant)
                        .roles(new HashSet<>())
                        .build());
        
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ApiException("Role not found: " + roleId));
        
        if (!role.getTenant().getId().equals(tenant.getId())) {
            throw new ApiException("Role does not belong to tenant");
        }
        
        assignment.getRoles().add(role);
        return permissionAssignmentRepository.save(assignment);
    }
    
    /**
     * Rimuove un ruolo da PermissionAssignment esistente.
     * 
     * @param permissionType Tipo della Permission
     * @param permissionId ID della Permission
     * @param roleId ID del ruolo da rimuovere
     * @param tenant Tenant di appartenenza
     * @return PermissionAssignment aggiornato
     */
    public PermissionAssignment removeRole(
            String permissionType,
            Long permissionId,
            Long roleId,
            Tenant tenant) {
        
        PermissionAssignment assignment = permissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndTenant(permissionType, permissionId, tenant)
                .orElseThrow(() -> new ApiException("PermissionAssignment not found"));
        
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ApiException("Role not found: " + roleId));
        
        assignment.getRoles().remove(role);
        
        // Se non ci sono più ruoli e grant, elimina PermissionAssignment
        if (assignment.getRoles().isEmpty() && assignment.getGrant() == null) {
            permissionAssignmentRepository.delete(assignment);
            return null;
        }
        
        return permissionAssignmentRepository.save(assignment);
    }
    
    /**
     * Crea un Grant e lo assegna a PermissionAssignment.
     * 
     * @param permissionType Tipo della Permission
     * @param permissionId ID della Permission
     * @param userIds Set di ID degli utenti da includere nel Grant
     * @param groupIds Set di ID dei gruppi da includere nel Grant
     * @param negatedUserIds Set di ID degli utenti da negare nel Grant
     * @param negatedGroupIds Set di ID dei gruppi da negare nel Grant
     * @param tenant Tenant di appartenenza
     * @return PermissionAssignment aggiornato
     */
    public PermissionAssignment createAndAssignGrant(
            String permissionType,
            Long permissionId,
            Set<Long> userIds,
            Set<Long> groupIds,
            Set<Long> negatedUserIds,
            Set<Long> negatedGroupIds,
            Tenant tenant) {
        
        PermissionAssignment assignment = permissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndTenant(permissionType, permissionId, tenant)
                .orElseGet(() -> PermissionAssignment.builder()
                        .permissionType(permissionType)
                        .permissionId(permissionId)
                        .tenant(tenant)
                        .roles(new HashSet<>())
                        .build());
        
        // Elimina Grant esistente se presente
        if (assignment.getGrant() != null) {
            Long oldGrantId = assignment.getGrant().getId();
            grantCleanupService.deleteGrantCompletely(oldGrantId);
        }
        
        // Crea nuovo Grant
        Grant grant = new Grant();
        grant.setRole(null); // Grant diretto, non associato a Role
        
        // Popola utenti
        if (userIds != null && !userIds.isEmpty()) {
            Set<User> users = new HashSet<>();
            for (Long userId : userIds) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ApiException("User not found: " + userId));
                users.add(user);
            }
            grant.setUsers(users);
        }
        
        // Popola gruppi
        if (groupIds != null && !groupIds.isEmpty()) {
            Set<Group> groups = new HashSet<>();
            for (Long groupId : groupIds) {
                Group group = groupRepository.findByIdAndTenant(groupId, tenant)
                        .orElseThrow(() -> new ApiException("Group not found: " + groupId));
                groups.add(group);
            }
            grant.setGroups(groups);
        }
        
        // Popola utenti negati
        if (negatedUserIds != null && !negatedUserIds.isEmpty()) {
            Set<User> negatedUsers = new HashSet<>();
            for (Long userId : negatedUserIds) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ApiException("User not found: " + userId));
                negatedUsers.add(user);
            }
            grant.setNegatedUsers(negatedUsers);
        }
        
        // Popola gruppi negati
        if (negatedGroupIds != null && !negatedGroupIds.isEmpty()) {
            Set<Group> negatedGroups = new HashSet<>();
            for (Long groupId : negatedGroupIds) {
                Group group = groupRepository.findByIdAndTenant(groupId, tenant)
                        .orElseThrow(() -> new ApiException("Group not found: " + groupId));
                negatedGroups.add(group);
            }
            grant.setNegatedGroups(negatedGroups);
        }
        
        grant = grantRepository.save(grant);
        assignment.setGrant(grant);
        
        return permissionAssignmentRepository.save(assignment);
    }
    
}


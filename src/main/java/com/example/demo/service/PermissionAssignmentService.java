package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servizio per gestire PermissionAssignment (assegnazioni globali di ruoli e grant a Permission).
 * 
 * NOTA: Questo servizio gestisce solo assegnazioni GLOBALI (project = null).
 * Per assegnazioni di progetto, usa PermissionAssignment direttamente con project != null.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PermissionAssignmentService {
    
    private final PermissionAssignmentRepository permissionAssignmentRepository;
    private final RoleRepository roleRepository;
    private final GrantRepository grantRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final GroupRepository groupRepository;
    private final GrantCleanupService grantCleanupService;
    private final WorkerPermissionRepository workerPermissionRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final CreatorPermissionRepository creatorPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    
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
        
        ItemTypeConfiguration configuration = resolveAndValidatePermission(permissionType, permissionId, tenant);
        boolean projectScoped = configuration.getScope() != ScopeType.TENANT;

        // Trova o crea PermissionAssignment GLOBALE (project = null)
        Optional<PermissionAssignment> existingOpt = permissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndTenantAndProjectIsNull(permissionType, permissionId, tenant);

        boolean isCleanupRequest = projectScoped && existingOpt.isPresent()
                && roleIds != null
                && roleIds.isEmpty()
                && grantId == null;

        if (projectScoped && !isCleanupRequest) {
            throw new ApiException("Permission belongs to a project-scoped ItemTypeSet. Use the project-specific assignment endpoint.");
        }
        
        PermissionAssignment assignment;
        if (existingOpt.isPresent()) {
            assignment = existingOpt.get();
        } else {
            assignment = PermissionAssignment.builder()
                    .permissionType(permissionType)
                    .permissionId(permissionId)
                    .tenant(tenant)
                    .project(null) // Assegnazione globale
                    .roles(new HashSet<>())
                    .build();
        }
        
        // Aggiorna ruoli
        if (roleIds != null) {
            assignment.setRoles(resolveRoles(roleIds, tenant));
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
    
    @Transactional(readOnly = true)
    public Map<Long, PermissionAssignment> getAssignments(
            String permissionType,
            Collection<Long> permissionIds,
            Tenant tenant
    ) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = permissionIds instanceof List<Long> list ? list : new ArrayList<>(permissionIds);
        List<PermissionAssignment> assignments = permissionAssignmentRepository
                .findAllByPermissionTypeAndPermissionIdInAndTenantWithCollections(permissionType, ids, tenant);
        return assignments.stream()
                .collect(Collectors.toMap(PermissionAssignment::getPermissionId, assignment -> assignment));
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
                .findByPermissionTypeAndPermissionIdAndTenantAndProjectIsNull(permissionType, permissionId, tenant);
        
        if (assignmentOpt.isPresent()) {
            PermissionAssignment assignment = assignmentOpt.get();
            
            // Scollega ruoli e grant prima di eliminare per evitare violazioni FK
            assignment.getRoles().clear();
            Long grantId = null;
            if (assignment.getGrant() != null) {
                grantId = assignment.getGrant().getId();
                assignment.setGrant(null);
            }
            permissionAssignmentRepository.save(assignment);
            
            // Elimina PermissionAssignment
            permissionAssignmentRepository.delete(assignment);
            
            // Ora puoi eliminare il Grant in sicurezza (se esiste)
            if (grantId != null) {
                grantCleanupService.deleteGrantCompletely(grantId);
            }
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
        
        ItemTypeConfiguration configuration = resolveAndValidatePermission(permissionType, permissionId, tenant);
        if (configuration.getScope() != ScopeType.TENANT) {
            throw new ApiException("Permission belongs to a project-scoped ItemTypeSet. Use the project-specific assignment endpoint.");
        }

        PermissionAssignment assignment = permissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndTenantAndProjectIsNull(permissionType, permissionId, tenant)
                .orElseGet(() -> PermissionAssignment.builder()
                        .permissionType(permissionType)
                        .permissionId(permissionId)
                        .tenant(tenant)
                        .project(null) // Assegnazione globale
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
        
        resolveAndValidatePermission(permissionType, permissionId, tenant);

        PermissionAssignment assignment = permissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndTenantAndProjectIsNull(permissionType, permissionId, tenant)
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
        
        ItemTypeConfiguration configuration = resolveAndValidatePermission(permissionType, permissionId, tenant);
        if (configuration.getScope() != ScopeType.TENANT) {
            throw new ApiException("Permission belongs to a project-scoped ItemTypeSet. Use the project-specific assignment endpoint.");
        }

        PermissionAssignment assignment = permissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndTenantAndProjectIsNull(permissionType, permissionId, tenant)
                .orElseGet(() -> PermissionAssignment.builder()
                        .permissionType(permissionType)
                        .permissionId(permissionId)
                        .tenant(tenant)
                        .project(null) // Assegnazione globale
                        .roles(new HashSet<>())
                        .build());
        
        Grant grant = resolveGrant(assignment);

        Set<User> users = buildUsersSet(userIds, tenant);
        Set<Group> groups = buildGroupsSet(groupIds, tenant);
        Set<User> negatedUsers = buildUsersSet(negatedUserIds, tenant);
        Set<Group> negatedGroups = buildGroupsSet(negatedGroupIds, tenant);

        replaceGrantCollections(grant, users, groups, negatedUsers, negatedGroups);

        grant = grantRepository.save(grant);

        assignment.setGrant(grant);
        
        return permissionAssignmentRepository.save(assignment);
    }

    private Grant resolveGrant(PermissionAssignment assignment) {
        if (assignment.getGrant() != null) {
            Long grantId = assignment.getGrant().getId();
            return grantRepository.findByIdWithCollections(grantId)
                    .orElseGet(assignment::getGrant);
        }
        Grant grant = new Grant();
        grant.setRole(null); // Grant diretto, non associato a Role
        return grant;
    }

    private Set<User> buildUsersSet(Set<Long> userIds, Tenant tenant) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashSet<>();
        }

        Set<Long> distinctIds = new HashSet<>(userIds);
        Map<Long, User> usersById = userRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        if (usersById.size() != distinctIds.size()) {
            Set<Long> missing = new HashSet<>(distinctIds);
            missing.removeAll(usersById.keySet());
            throw new ApiException("User not found: " + missing.iterator().next());
        }

        Set<Long> allowedIds = userRoleRepository.findUserIdsByTenantAndUserIdIn(tenant.getId(), distinctIds);
        if (allowedIds.size() != distinctIds.size()) {
            Set<Long> missing = new HashSet<>(distinctIds);
            missing.removeAll(allowedIds);
            throw new ApiException("User " + missing.iterator().next() + " does not belong to tenant " + tenant.getId());
        }

        return new HashSet<>(usersById.values());
    }

    private Set<Group> buildGroupsSet(Set<Long> groupIds, Tenant tenant) {
        if (groupIds == null || groupIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> distinctIds = new HashSet<>(groupIds);
        Map<Long, Group> groupsById = groupRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(
                        Group::getId,
                        Function.identity(),
                        (existing, duplicate) -> existing));

        if (groupsById.size() != distinctIds.size()) {
            Set<Long> missing = new HashSet<>(distinctIds);
            missing.removeAll(groupsById.keySet());
            throw new ApiException("Group not found: " + missing.iterator().next());
        }

        groupsById.values().forEach(group -> {
            if (group.getTenant() == null || !group.getTenant().getId().equals(tenant.getId())) {
                throw new ApiException("Group not found: " + group.getId());
            }
        });

        return new HashSet<>(groupsById.values());
    }

    private void replaceGrantCollections(Grant grant,
                                         Set<User> users,
                                         Set<Group> groups,
                                         Set<User> negatedUsers,
                                         Set<Group> negatedGroups) {
        grant.getUsers().clear();
        grant.getUsers().addAll(users);

        grant.getGroups().clear();
        grant.getGroups().addAll(groups);

        grant.getNegatedUsers().clear();
        grant.getNegatedUsers().addAll(negatedUsers);

        grant.getNegatedGroups().clear();
        grant.getNegatedGroups().addAll(negatedGroups);
    }

    private ItemTypeConfiguration resolveAndValidatePermission(String permissionType, Long permissionId, Tenant tenant) {
        ItemTypeConfiguration configuration = resolveItemTypeConfiguration(permissionType, permissionId);
        if (configuration == null) {
            throw new ApiException("Permission not found: " + permissionType + " #" + permissionId);
        }
        if (!configuration.getTenant().getId().equals(tenant.getId())) {
            throw new ApiException("Permission does not belong to tenant");
        }
        return configuration;
    }

    private ItemTypeConfiguration resolveItemTypeConfiguration(String permissionType, Long permissionId) {
        return switch (permissionType) {
            case "WorkerPermission" -> workerPermissionRepository.findById(permissionId)
                    .map(WorkerPermission::getItemTypeConfiguration)
                    .orElse(null);
            case "StatusOwnerPermission" -> statusOwnerPermissionRepository.findById(permissionId)
                    .map(StatusOwnerPermission::getItemTypeConfiguration)
                    .orElse(null);
            case "FieldOwnerPermission" -> fieldOwnerPermissionRepository.findById(permissionId)
                    .map(FieldOwnerPermission::getItemTypeConfiguration)
                    .orElse(null);
            case "CreatorPermission" -> creatorPermissionRepository.findById(permissionId)
                    .map(CreatorPermission::getItemTypeConfiguration)
                    .orElse(null);
            case "ExecutorPermission" -> executorPermissionRepository.findById(permissionId)
                    .map(ExecutorPermission::getItemTypeConfiguration)
                    .orElse(null);
            case "FieldStatusPermission" -> fieldStatusPermissionRepository.findById(permissionId)
                    .map(FieldStatusPermission::getItemTypeConfiguration)
                    .orElse(null);
            default -> throw new ApiException("Unsupported permission type: " + permissionType);
        };
    }

    private Set<Role> resolveRoles(Set<Long> roleIds, Tenant tenant) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new HashSet<>();
        }

        Set<Long> distinctIds = new HashSet<>(roleIds);
        Map<Long, Role> rolesById = roleRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(
                        Role::getId,
                        Function.identity(),
                        (existing, duplicate) -> existing));

        if (rolesById.size() != distinctIds.size()) {
            Set<Long> missing = new HashSet<>(distinctIds);
            missing.removeAll(rolesById.keySet());
            throw new ApiException("Role not found: " + missing.iterator().next());
        }

        for (Role role : rolesById.values()) {
            if (role.getTenant() == null || !role.getTenant().getId().equals(tenant.getId())) {
                throw new ApiException("Role does not belong to tenant");
            }
        }

        return new HashSet<>(rolesById.values());
    }
}


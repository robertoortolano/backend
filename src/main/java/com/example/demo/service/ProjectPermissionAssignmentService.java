package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servizio per gestire PermissionAssignment di progetto (assegnazioni specifiche per progetto).
 * 
 * NOTA: Questo servizio gestisce PermissionAssignment con project != null.
 * Le assegnazioni sono completamente indipendenti da quelle globali (project = null).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ProjectPermissionAssignmentService {
    
    private final PermissionAssignmentRepository permissionAssignmentRepository;
    private final ProjectRepository projectRepository;
    private final RoleRepository roleRepository;
    private final GrantRepository grantRepository;
    private final GrantCleanupService grantCleanupService;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final UserRoleRepository userRoleRepository;
    private final WorkerPermissionRepository workerPermissionRepository;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldOwnerPermissionRepository fieldOwnerPermissionRepository;
    private final CreatorPermissionRepository creatorPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    
    /**
     * Crea o aggiorna un PermissionAssignment per una Permission e un progetto.
     * 
     * @param permissionType Tipo della Permission (es. "FieldOwnerPermission")
     * @param permissionId ID della Permission
     * @param projectId ID del progetto
     * @param itemTypeSetId ID dell'ItemTypeSet
     * @param roleIds Set di ID dei ruoli custom da assegnare (può essere null/empty)
     * @param grantId ID del Grant da assegnare (può essere null)
     * @param tenant Tenant di appartenenza
     * @return PermissionAssignment creato o aggiornato (con project != null)
     */
    public PermissionAssignment createOrUpdateProjectAssignment(
            String permissionType,
            Long permissionId,
            Long projectId,
            Long itemTypeSetId,
            Set<Long> roleIds,
            Long grantId,
            Tenant tenant) {
        
        String normalizedPermissionType = normalizePermissionType(permissionType);

        // Verifica che il progetto esista
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        ItemTypeSet itemTypeSet = resolveProjectItemTypeSet(project, itemTypeSetId);
        resolveAndValidatePermissionForProject(normalizedPermissionType, permissionId, tenant, project, itemTypeSet);
        
        // Trova o crea PermissionAssignment di progetto
        Optional<PermissionAssignment> existingOpt = permissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndTenantAndProject(normalizedPermissionType, permissionId, tenant, project);
        
        PermissionAssignment assignment;
        if (existingOpt.isPresent()) {
            assignment = existingOpt.get();
        } else {
            // Crea nuovo PermissionAssignment per il progetto
            assignment = PermissionAssignment.builder()
                    .permissionType(normalizedPermissionType)
                    .permissionId(permissionId)
                    .tenant(tenant)
                    .project(project) // Assegnazione di progetto
                    .itemTypeSet(itemTypeSet)
                    .roles(new HashSet<>())
                    .build();
        }
        
        // Aggiorna ruoli
        if (roleIds != null) {
            Set<Role> roles = new HashSet<>();
            for (Long roleId : roleIds) {
                Role role = roleRepository.findById(roleId)
                        .orElseThrow(() -> new ApiException("Role not found: " + roleId));
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
        
        // Assicura che itemTypeSet sia aggiornato
        assignment.setItemTypeSet(itemTypeSet);
        
        return permissionAssignmentRepository.save(assignment);
    }
    
    /**
     * Ottiene PermissionAssignment per una Permission e un progetto.
     * 
     * @param permissionType Tipo della Permission
     * @param permissionId ID della Permission
     * @param projectId ID del progetto
     * @param tenant Tenant di appartenenza
     * @return PermissionAssignment se esiste, altrimenti Optional.empty()
     */
    @Transactional(readOnly = true)
    public Optional<PermissionAssignment> getProjectAssignment(
            String permissionType,
            Long permissionId,
            Long projectId,
            Tenant tenant
    ) {
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));

        ItemTypeSet itemTypeSet = project.getItemTypeSet();

        String normalizedPermissionType = normalizePermissionType(permissionType);

        resolveAndValidatePermissionForProject(
                normalizedPermissionType,
                permissionId,
                tenant,
                project,
                itemTypeSet
        );
        return permissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndTenantAndProjectWithCollections(normalizedPermissionType, permissionId, tenant, project);
    }
    
    @Transactional(readOnly = true)
    public Map<Long, PermissionAssignment> getProjectAssignments(
            String permissionType,
            Collection<Long> permissionIds,
            Long projectId,
            Tenant tenant
    ) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        List<Long> ids = permissionIds instanceof List<Long> list ? list : new ArrayList<>(permissionIds);
        String normalizedPermissionType = normalizePermissionType(permissionType);
        List<PermissionAssignment> assignments = permissionAssignmentRepository
                .findAllByPermissionTypeAndPermissionIdInAndTenantAndProjectWithCollections(normalizedPermissionType, ids, tenant, project);
        
        return assignments.stream()
                .collect(java.util.stream.Collectors.toMap(PermissionAssignment::getPermissionId, assignment -> assignment));
    }
    
    /**
     * Elimina PermissionAssignment per una Permission e un progetto.
     * Elimina anche il Grant associato se esiste.
     * 
     * @param permissionType Tipo della Permission
     * @param permissionId ID della Permission
     * @param projectId ID del progetto
     * @param tenant Tenant di appartenenza
     */
    public void deleteProjectAssignment(
            String permissionType,
            Long permissionId,
            Long projectId,
            Tenant tenant) {
        
        String normalizedPermissionType = normalizePermissionType(permissionType);
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        ItemTypeSet itemTypeSet = resolveProjectItemTypeSet(project, project.getItemTypeSet() != null ? project.getItemTypeSet().getId() : null);
        resolveAndValidatePermissionForProject(normalizedPermissionType, permissionId, tenant, project, itemTypeSet);
        
        Optional<PermissionAssignment> assignmentOpt = permissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndTenantAndProject(normalizedPermissionType, permissionId, tenant, project);
        
        if (assignmentOpt.isPresent()) {
            PermissionAssignment assignment = assignmentOpt.get();
            
            assignment.getRoles().clear();
            Long grantId = null;
            if (assignment.getGrant() != null) {
                grantId = assignment.getGrant().getId();
                assignment.setGrant(null);
            }
            permissionAssignmentRepository.save(assignment);
            
            permissionAssignmentRepository.delete(assignment);
            
            if (grantId != null) {
                grantCleanupService.deleteGrantCompletely(grantId);
            }
        }
    }
    
    /**
     * Crea un Grant e lo assegna a PermissionAssignment di progetto.
     * 
     * @param permissionType Tipo della Permission
     * @param permissionId ID della Permission
     * @param projectId ID del progetto
     * @param itemTypeSetId ID dell'ItemTypeSet
     * @param userIds Set di ID degli utenti da includere nel Grant
     * @param groupIds Set di ID dei gruppi da includere nel Grant
     * @param negatedUserIds Set di ID degli utenti da negare nel Grant
     * @param negatedGroupIds Set di ID dei gruppi da negare nel Grant
     * @param tenant Tenant di appartenenza
     * @return PermissionAssignment aggiornato (con project != null)
     */
    public PermissionAssignment createAndAssignGrant(
            String permissionType,
            Long permissionId,
            Long projectId,
            Long itemTypeSetId,
            Set<Long> userIds,
            Set<Long> groupIds,
            Set<Long> negatedUserIds,
            Set<Long> negatedGroupIds,
            Tenant tenant) {
        
        // Verifica che il progetto esista
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        ItemTypeSet itemTypeSet = resolveProjectItemTypeSet(project, itemTypeSetId);
        String normalizedPermissionType = normalizePermissionType(permissionType);
        resolveAndValidatePermissionForProject(normalizedPermissionType, permissionId, tenant, project, itemTypeSet);
        
        // Trova o crea PermissionAssignment di progetto
        Optional<PermissionAssignment> existingOpt = permissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndTenantAndProject(normalizedPermissionType, permissionId, tenant, project);
        
        PermissionAssignment assignment;
        if (existingOpt.isPresent()) {
            assignment = existingOpt.get();
        } else {
            assignment = PermissionAssignment.builder()
                    .permissionType(normalizedPermissionType)
                    .permissionId(permissionId)
                    .tenant(tenant)
                    .project(project) // Assegnazione di progetto
                    .itemTypeSet(itemTypeSet)
                    .roles(new HashSet<>())
                    .build();
        }
        
        Grant grant = resolveGrant(assignment);

        Set<User> users = buildUsersSet(userIds, tenant);
        Set<Group> groups = buildGroupsSet(groupIds, tenant);
        Set<User> negatedUsers = buildUsersSet(negatedUserIds, tenant);
        Set<Group> negatedGroups = buildGroupsSet(negatedGroupIds, tenant);

        replaceGrantCollections(grant, users, groups, negatedUsers, negatedGroups);

        grant = grantRepository.save(grant);
        assignment.setGrant(grant);
        
        // Assicura che itemTypeSet sia aggiornato
        assignment.setItemTypeSet(itemTypeSet);
        
        return permissionAssignmentRepository.save(assignment);
    }

    private ItemTypeSet resolveProjectItemTypeSet(Project project, Long requestedItemTypeSetId) {
        ItemTypeSet projectItemTypeSet = project.getItemTypeSet();
        if (projectItemTypeSet == null) {
            throw new ApiException("Project has no ItemTypeSet assigned");
        }

        if (requestedItemTypeSetId != null && !projectItemTypeSet.getId().equals(requestedItemTypeSetId)) {
            throw new ApiException("ItemTypeSet not assigned to project");
        }

        return projectItemTypeSet;
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
        Set<Group> groups = new HashSet<>();
        for (Long groupId : groupIds) {
            Group group = groupRepository.findByIdAndTenant(groupId, tenant)
                    .orElseThrow(() -> new ApiException("Group not found: " + groupId));
            groups.add(group);
        }
        return groups;
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

    /**
     * Elimina tutte le PermissionAssignment di progetto per un ItemTypeSet.
     * Utile quando si elimina un ItemTypeSet.
     * 
     * @param itemTypeSetId ID dell'ItemTypeSet
     * @param tenantId ID del tenant
     */
    public void deleteByItemTypeSet(Long itemTypeSetId, Long tenantId) {
        // Trova tutte le PermissionAssignment di progetto per questo ItemTypeSet
        List<PermissionAssignment> projectAssignments = permissionAssignmentRepository
                .findByItemTypeSetIdAndProjectIsNotNull(itemTypeSetId);
        
        for (PermissionAssignment assignment : projectAssignments) {
            // Elimina Grant se esiste
            if (assignment.getGrant() != null) {
                Long grantId = assignment.getGrant().getId();
                grantCleanupService.deleteGrantCompletely(grantId);
            }
            
            // Elimina PermissionAssignment
            permissionAssignmentRepository.delete(assignment);
        }
    }
    
    private ItemTypeConfiguration resolveAndValidatePermissionForProject(
            String permissionType,
            Long permissionId,
            Tenant tenant,
            Project project,
            ItemTypeSet itemTypeSet
    ) {
        String normalizedPermissionType = normalizePermissionType(permissionType);

        ItemTypeConfiguration configuration = resolveItemTypeConfiguration(normalizedPermissionType, permissionId);
        if (configuration == null) {
            throw new ApiException("Permission not found: " + normalizedPermissionType + " #" + permissionId);
        }
        if (!configuration.getTenant().getId().equals(tenant.getId())) {
            throw new ApiException("Permission does not belong to tenant");
        }
        boolean belongsToItemTypeSet = itemTypeSet.getItemTypeConfigurations().stream()
                .anyMatch(config -> Objects.equals(config.getId(), configuration.getId()));
        if (!belongsToItemTypeSet) {
            throw new ApiException("Permission does not belong to the project's ItemTypeSet");
        }
        if (configuration.getScope() == ScopeType.PROJECT) {
            if (configuration.getProject() == null || !Objects.equals(configuration.getProject().getId(), project.getId())) {
                throw new ApiException("Permission belongs to a different project");
            }
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

    private String normalizePermissionType(String permissionType) {
        if (permissionType == null || permissionType.isBlank()) {
            return permissionType;
        }

        String trimmed = permissionType.trim();

        if (trimmed.endsWith("Permission")) {
            return trimmed;
        }

        return switch (trimmed.toUpperCase()) {
            case "WORKERS" -> "WorkerPermission";
            case "STATUS_OWNERS" -> "StatusOwnerPermission";
            case "FIELD_OWNERS" -> "FieldOwnerPermission";
            case "CREATORS" -> "CreatorPermission";
            case "EXECUTORS" -> "ExecutorPermission";
            case "EDITORS", "FIELD_EDITORS" -> "FieldStatusPermission";
            case "VIEWERS", "FIELD_VIEWERS" -> "FieldStatusPermission";
            default -> trimmed;
        };
    }
    
}

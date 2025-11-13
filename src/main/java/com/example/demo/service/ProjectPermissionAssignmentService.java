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
    private final ItemTypeSetRepository itemTypeSetRepository;
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

        // Usa resolveProjectItemTypeSet per caricare l'ItemTypeSet con le configurazioni
        ItemTypeSet itemTypeSet = resolveProjectItemTypeSet(project, null);

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
        
        try {
            String normalizedPermissionType = normalizePermissionType(permissionType);
            Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                    .orElseThrow(() -> new ApiException("Project not found"));
            ItemTypeSet itemTypeSet = resolveProjectItemTypeSet(project, project.getItemTypeSet() != null ? project.getItemTypeSet().getId() : null);
            
            // Se la permission non esiste più, ignora silenziosamente (idempotenza)
            try {
                resolveAndValidatePermissionForProject(normalizedPermissionType, permissionId, tenant, project, itemTypeSet);
            } catch (ApiException e) {
                // Se la permission non esiste più, è già stata eliminata - ignora (idempotenza)
                if (e.getMessage() != null && e.getMessage().contains("Permission not found")) {
                    return;
                }
                // Rilancia altre eccezioni
                throw e;
            }
            
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
        } catch (ApiException e) {
            // Se la permission o l'assegnazione sono già state eliminate, ignora l'eccezione (idempotenza)
            // Solo se l'eccezione indica che la permission non esiste
            if (e.getMessage() != null && (e.getMessage().contains("Permission not found") || 
                e.getMessage().contains("does not belong"))) {
                return;
            }
            // Rilancia altre eccezioni
            throw e;
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

        ItemTypeSet itemTypeSetToUse = projectItemTypeSet;
        
        // Se è stato richiesto un ItemTypeSet specifico diverso da quello assegnato
        if (requestedItemTypeSetId != null && !projectItemTypeSet.getId().equals(requestedItemTypeSetId)) {
            // Verifica se l'ItemTypeSet richiesto è un ItemTypeSet di progetto che appartiene allo stesso progetto
            // Questo permette di creare assegnazioni per ItemTypeSet di progetto anche se non sono quello attualmente assegnato
            ItemTypeSet requestedItemTypeSet = itemTypeSetRepository.findByIdWithItemTypeConfigurationsAndTenant(
                    requestedItemTypeSetId, 
                    project.getTenant()
            ).orElseThrow(() -> new ApiException("ItemTypeSet not found or does not belong to tenant"));
            
            // Verifica che l'ItemTypeSet richiesto sia di progetto e appartenga allo stesso progetto
            if (requestedItemTypeSet.getScope() == ScopeType.PROJECT) {
                if (requestedItemTypeSet.getProject() == null || !requestedItemTypeSet.getProject().getId().equals(project.getId())) {
                    throw new ApiException("ItemTypeSet does not belong to the specified project");
                }
                // L'ItemTypeSet richiesto è valido, usalo
                itemTypeSetToUse = requestedItemTypeSet;
            } else {
                // L'ItemTypeSet richiesto è globale, ma il progetto ha un ItemTypeSet assegnato
                // Per ora, permettiamo solo ItemTypeSet di progetto quando si specifica un ID diverso
                // Se necessario, possiamo permettere anche ItemTypeSet globali in futuro
                throw new ApiException("ItemTypeSet not assigned to project. To use a different ItemTypeSet, it must be a project-scoped ItemTypeSet for this project.");
            }
        }

        // Carica esplicitamente l'ItemTypeSet con le configurazioni per evitare problemi di lazy loading
        // Questo è necessario perché la validazione in resolveAndValidatePermissionForProject
        // controlla se la configurazione appartiene all'ItemTypeSet
        ItemTypeSet loadedItemTypeSet = itemTypeSetRepository.findByIdWithItemTypeConfigurationsAndTenant(
                itemTypeSetToUse.getId(), 
                project.getTenant()
        ).orElseThrow(() -> new ApiException("ItemTypeSet not found or does not belong to tenant"));
        
        // Verifica aggiuntiva: se l'ItemTypeSet è di progetto, deve appartenere al progetto specifico
        if (loadedItemTypeSet.getScope() == ScopeType.PROJECT) {
            if (loadedItemTypeSet.getProject() == null || !loadedItemTypeSet.getProject().getId().equals(project.getId())) {
                throw new ApiException("ItemTypeSet does not belong to the specified project");
            }
        }
        
        return loadedItemTypeSet;
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
        
        // Verifica che la configurazione appartenga all'ItemTypeSet del progetto
        // Estraiamo gli ID delle configurazioni per evitare problemi di lazy loading e confronti tra istanze diverse
        Set<Long> itemTypeSetConfigurationIds = itemTypeSet.getItemTypeConfigurations().stream()
                .map(ItemTypeConfiguration::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        
        // Verifica aggiuntiva: se l'ItemTypeSet non ha configurazioni caricate, ricaricalo
        if (itemTypeSetConfigurationIds.isEmpty() && itemTypeSet.getId() != null) {
            // Ricarica l'ItemTypeSet con le configurazioni
            ItemTypeSet reloadedItemTypeSet = itemTypeSetRepository.findByIdWithItemTypeConfigurationsAndTenant(
                    itemTypeSet.getId(), 
                    tenant
            ).orElseThrow(() -> new ApiException("ItemTypeSet not found or does not belong to tenant"));
            itemTypeSetConfigurationIds = reloadedItemTypeSet.getItemTypeConfigurations().stream()
                    .map(ItemTypeConfiguration::getId)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            // Aggiorna anche l'ItemTypeSet originale con le configurazioni ricaricate
            itemTypeSet.setItemTypeConfigurations(reloadedItemTypeSet.getItemTypeConfigurations());
        }
        
        // Verifica diretta nel database che la configurazione appartenga all'ItemTypeSet specifico
        // Questo è importante per evitare problemi di lazy loading
        boolean belongsToItemTypeSet = configuration.getId() != null && itemTypeSetConfigurationIds.contains(configuration.getId());
        
        // Verifica aggiuntiva: controlla direttamente nel database se la configurazione appartiene all'ItemTypeSet
        if (belongsToItemTypeSet && itemTypeSet.getId() != null) {
            List<ItemTypeSet> itemTypeSetsWithConfig = itemTypeSetRepository.findByItemTypeConfigurations_IdAndTenant(
                    configuration.getId(), 
                    tenant
            );
            boolean belongsToThisItemTypeSet = itemTypeSetsWithConfig.stream()
                    .anyMatch(its -> Objects.equals(its.getId(), itemTypeSet.getId()));
            if (!belongsToThisItemTypeSet) {
                belongsToItemTypeSet = false;
            }
        }
        
        if (!belongsToItemTypeSet) {
            // Verifica se la configurazione appartiene a un ItemTypeSet di progetto associato allo stesso progetto
            // Questo è necessario quando si visualizzano le permission di un ItemTypeSet di progetto
            // che non è quello attualmente assegnato al progetto
            List<ItemTypeSet> itemTypeSetsWithConfig = itemTypeSetRepository.findByItemTypeConfigurations_IdAndTenant(
                    configuration.getId(), 
                    tenant
            );
            boolean belongsToProjectItemTypeSet = itemTypeSetsWithConfig.stream()
                    .anyMatch(its -> {
                        // Verifica se è un ItemTypeSet di progetto associato allo stesso progetto
                        if (its.getScope() == ScopeType.PROJECT && its.getProject() != null) {
                            return Objects.equals(its.getProject().getId(), project.getId());
                        }
                        return false;
                    });
            
            if (belongsToProjectItemTypeSet) {
                // La configurazione appartiene a un ItemTypeSet di progetto associato al progetto
                // Questo è valido, anche se non è l'ItemTypeSet attualmente assegnato al progetto
                return configuration;
            }
            
            // Verifica aggiuntiva: controlla se la configurazione appartiene a qualche ItemTypeSet del tenant
            // per dare un messaggio di errore più informativo
            boolean existsInAnyItemTypeSet = itemTypeSetRepository.existsByItemTypeConfigurations_IdAndTenant_Id(
                    configuration.getId(), 
                    tenant.getId()
            );
            if (existsInAnyItemTypeSet) {
                throw new ApiException(
                    String.format("Permission does not belong to the project's ItemTypeSet (ID: %d). " +
                            "The permission's ItemTypeConfiguration (ID: %d) belongs to a different ItemTypeSet. " +
                            "ItemTypeSet has %d configurations loaded.",
                            itemTypeSet.getId(), configuration.getId(), itemTypeSetConfigurationIds.size())
                );
            } else {
                throw new ApiException(
                    String.format("Permission does not belong to the project's ItemTypeSet (ID: %d). " +
                            "The permission's ItemTypeConfiguration (ID: %d) does not exist in any ItemTypeSet. " +
                            "ItemTypeSet has %d configurations loaded.",
                            itemTypeSet.getId(), configuration.getId(), itemTypeSetConfigurationIds.size())
                );
            }
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

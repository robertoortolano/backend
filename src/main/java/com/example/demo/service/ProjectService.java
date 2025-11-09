package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.initializer.ProjectInitializer;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service per gestione Project.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final UserFavoriteProjectRepository userFavoriteProjectRepository;

    private final GrantRepository grantRepository;
    private final GrantRoleAssignmentRepository grantRoleAssignmentRepository;

    private final GrantRoleLookup grantRoleLookup;
    private final ItemTypeSetLookup itemTypeSetLookup;
    private final ProjectLookup projectLookup;

    private final DtoMapperFacade dtoMapper;
    private final List<ProjectInitializer> projectInitializers;


    @Transactional(readOnly = true)
    public List<Project> getAllProjects(Tenant tenant) {
        return projectRepository.findAllByTenant(tenant);
    }

    @Transactional(readOnly = true)
    public ProjectViewDto getById(Tenant tenant, Long id) {
        return dtoMapper.toProjectViewDto(projectLookup.getById(tenant, id));
    }


    public ProjectViewDto createProjectForCurrentUser(Tenant tenant, User user, ProjectCreateDto dto) {

        boolean authorized = grantRoleLookup.existsByUserGlobal(user, tenant, "ADMIN");

        if (!authorized) {
            throw new ApiException("Accesso negato: non sei TENANT_ADMIN per questa tenant");
        }

        if (projectRepository.existsByProjectKeyAndTenantId(dto.key(), tenant.getId())) {
            throw new ApiException("Project key già esistente");
        }

        Project project = dtoMapper.toProject(dto);
        project.setTenant(tenant);
        projectInitializers.forEach(init -> init.initialize(project, tenant));
        projectRepository.save(project);

        // Assegna ruolo ADMIN a livello PROJECT usando UserRole
        UserRole projectAdminRole = UserRole.builder()
                .user(user)
                .tenant(tenant)
                .project(project)
                .roleName("ADMIN")
                .scope(ScopeType.PROJECT)
                .build();
        userRoleRepository.save(projectAdminRole);

        return dtoMapper.toProjectViewDto(project);
    }


    @Transactional(readOnly = true)
    public List<ProjectViewDto> getProjectsForUser(Tenant tenant, User user) {

        // Controllo se l'utente ha un ruolo globale da ADMIN sul tenant
        boolean isTenantAdmin = grantRoleLookup.existsByUserGlobal(user, tenant, "ADMIN");

        if (isTenantAdmin) {
            // Se è admin tenant, restituisco tutti i progetti della tenant
            return dtoMapper.toProjectViewDtos(projectRepository.findByTenant(tenant));
        }

        // Altrimenti restituisco solo i progetti dove ha ruolo di PROJECT_ADMIN
        return dtoMapper.toProjectViewDtos(grantRoleLookup.getProjectsByUserAndProjectRole(
                user, tenant, "ADMIN"
        ));
    }



    public ProjectViewDto updateProject(Tenant tenant, Long projectId, ProjectUpdateDto dto) {

        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found: " + projectId));

        projectRepository.findByProjectKeyAndTenant(dto.key(), tenant)
                .filter(p -> !p.getId().equals(projectId))
                .ifPresent(p -> {
                    throw new ApiException("Esiste già un project con questa key.");
                });

        // Applica le modifiche tramite mapper
        dtoMapper.updateProjectFromDto(dto, project);
        if (dto.itemTypeSetId() != null) {
            ItemTypeSet itemTypeSet = itemTypeSetLookup.getById(tenant, dto.itemTypeSetId());

            project.setItemTypeSet(itemTypeSet);
        }


        // Imposta tenant se non lo vuoi toccare via DTO
        project.setTenant(tenant);

        return dtoMapper.toProjectViewDto(projectRepository.save(project));
    }


    public void assignItemTypeSet(Tenant tenant, Long projectId, Long setId, User user) {
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Progetto non trovato"));

        ItemTypeSet itemTypeSet = itemTypeSetLookup.getById(tenant, setId);

        if (itemTypeSet.getScope() == ScopeType.PROJECT) {
            Project ownerProject = itemTypeSet.getProject();
            if (ownerProject == null || !ownerProject.getId().equals(project.getId())) {
                throw new ApiException("ItemTypeSet non assegnabile: appartiene a un altro progetto");
            }
        }

        project.setItemTypeSet(itemTypeSet);
        projectRepository.save(project);
    }

    /**
     * Assegna un utente come PROJECT_ADMIN per un progetto specifico
     */
    public void assignProjectAdmin(Long projectId, Long userId, Tenant tenant) {
        // Verifica che il progetto esista e appartenga alla tenant
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        // Verifica che l'utente esista
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found"));
        
        // Verifica che l'utente abbia accesso alla tenant
        if (!userRoleRepository.hasAccessToTenant(userId, tenant.getId())) {
            throw new ApiException("User must have access to tenant before being assigned as project admin");
        }
        
        // Verifica se l'utente è già PROJECT_ADMIN di questo progetto
        boolean alreadyAdmin = userRoleRepository.existsByUserIdAndProjectIdAndRoleName(
                userId, projectId, "ADMIN");
        
        if (alreadyAdmin) {
            throw new ApiException("User is already PROJECT_ADMIN of this project");
        }
        
        // Crea UserRole con scope PROJECT
        UserRole projectAdminRole = UserRole.builder()
                .user(user)
                .tenant(tenant)
                .project(project)
                .roleName("ADMIN")
                .scope(ScopeType.PROJECT)
                .build();
        
        userRoleRepository.save(projectAdminRole);
    }
    
    /**
     * Revoca il ruolo PROJECT_ADMIN di un utente da un progetto specifico
     */
    public void revokeProjectAdmin(Long projectId, Long userId, Tenant tenant) {
        // Verifica che il progetto esista e appartenga alla tenant
        projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        // Verifica che l'utente sia effettivamente PROJECT_ADMIN del progetto
        boolean isAdmin = userRoleRepository.existsByUserIdAndProjectIdAndRoleName(
                userId, projectId, "ADMIN");
        
        if (!isAdmin) {
            throw new ApiException("User is not PROJECT_ADMIN of this project");
        }
        
        // Rimuovi il ruolo PROJECT_ADMIN per questo progetto
        userRoleRepository.deleteByUserIdAndProjectIdAndRoleName(userId, projectId, "ADMIN");
    }
    
    /**
     * Ottiene tutti i progetti per cui un utente è PROJECT_ADMIN
     */
    @Transactional(readOnly = true)
    public List<ProjectViewDto> getProjectsWhereUserIsAdmin(Long userId, Tenant tenant) {
        // Ottieni tutti i progetti della tenant dove l'utente è PROJECT_ADMIN
        List<Project> projects = userRoleRepository.findProjectsByUserIdAndTenantIdAndRoleName(
                userId, tenant.getId(), "ADMIN");
        
        return dtoMapper.toProjectViewDtos(projects);
    }

    /**
     * Ottiene tutti i membri di un progetto con i loro ruoli
     * Include anche i TENANT_ADMIN (con flag isTenantAdmin = true)
     */
    @Transactional(readOnly = true)
    public List<ProjectMemberDto> getProjectMembers(Long projectId, Tenant tenant) {
        // Verifica che il progetto esista e appartenga alla tenant
        projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        List<ProjectMemberDto> members = new ArrayList<>();
        
        // 1. Aggiungi tutti i TENANT_ADMIN (hanno accesso automatico a tutti i progetti)
        List<User> tenantAdmins = userRoleRepository.findUsersByTenantIdAndRoleName(tenant.getId(), "ADMIN");
        for (User admin : tenantAdmins) {
            ProjectMemberDto dto = dtoMapper.toProjectMemberDto(admin);
            members.add(new ProjectMemberDto(
                    dto.userId(),
                    dto.username(),
                    dto.fullName(),
                    "ADMIN",
                    true  // isTenantAdmin = true
            ));
        }
        
        // 2. Aggiungi i membri PROJECT-level (che non sono TENANT_ADMIN)
        List<UserRole> projectRoles = userRoleRepository.findByProjectIdAndScope(projectId, ScopeType.PROJECT);
        
        for (UserRole ur : projectRoles) {
            // Verifica se l'utente è TENANT_ADMIN
            boolean isTenantAdmin = userRoleRepository.existsByUserIdAndTenantIdAndRoleName(
                    ur.getUser().getId(), tenant.getId(), "ADMIN");
            
            // Aggiungi solo se NON è TENANT_ADMIN (sono già stati aggiunti sopra)
            if (!isTenantAdmin) {
                ProjectMemberDto dto = dtoMapper.toProjectMemberDto(ur.getUser());
                members.add(new ProjectMemberDto(
                        dto.userId(),
                        dto.username(),
                        dto.fullName(),
                        ur.getRoleName(),
                        false  // isTenantAdmin = false
                ));
            }
        }
        
        return members;
    }

    /**
     * Aggiunge un membro a un progetto con un ruolo specifico
     * Nota: I TENANT_ADMIN non possono essere aggiunti (hanno già accesso automatico)
     */
    public void addProjectMember(Long projectId, Long userId, String roleName, Tenant tenant) {
        // Validazione roleName
        if (!roleName.equals("ADMIN") && !roleName.equals("USER")) {
            throw new ApiException("Invalid role name. Must be ADMIN or USER");
        }
        
        // Verifica che il progetto esista e appartenga alla tenant
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        // Verifica che l'utente esista
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found"));
        
        // Verifica che l'utente abbia accesso alla tenant
        if (!userRoleRepository.hasAccessToTenant(userId, tenant.getId())) {
            throw new ApiException("User must have access to tenant before being assigned to a project");
        }
        
        // Verifica che l'utente NON sia TENANT_ADMIN
        boolean isTenantAdmin = userRoleRepository.existsByUserIdAndTenantIdAndRoleName(
                userId, tenant.getId(), "ADMIN");
        
        if (isTenantAdmin) {
            throw new ApiException("TENANT_ADMIN users have automatic access to all projects and cannot be added manually");
        }
        
        // Verifica se l'utente ha già un ruolo in questo progetto
        boolean alreadyHasRole = userRoleRepository.existsByUserIdAndProjectIdAndScope(
                userId, projectId, ScopeType.PROJECT);
        
        if (alreadyHasRole) {
            throw new ApiException("User already has a role in this project. Use update instead.");
        }
        
        // Crea UserRole con scope PROJECT
        UserRole projectRole = UserRole.builder()
                .user(user)
                .tenant(tenant)
                .project(project)
                .roleName(roleName)
                .scope(ScopeType.PROJECT)
                .build();
        
        userRoleRepository.save(projectRole);
    }

    /**
     * Aggiorna il ruolo di un membro del progetto
     * Nota: Non permette di modificare TENANT_ADMIN
     */
    public void updateProjectMemberRole(Long projectId, Long userId, String newRoleName, Tenant tenant) {
        // Validazione roleName
        if (!newRoleName.equals("ADMIN") && !newRoleName.equals("USER")) {
            throw new ApiException("Invalid role name. Must be ADMIN or USER");
        }
        
        // Verifica che il progetto esista e appartenga alla tenant
        projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        // Verifica che l'utente NON sia TENANT_ADMIN
        boolean isTenantAdmin = userRoleRepository.existsByUserIdAndTenantIdAndRoleName(
                userId, tenant.getId(), "ADMIN");
        
        if (isTenantAdmin) {
            throw new ApiException("Cannot modify role of TENANT_ADMIN users");
        }
        
        // Trova il ruolo attuale dell'utente
        List<UserRole> currentRoles = userRoleRepository.findByUserIdAndProjectIdAndScope(
                userId, projectId, ScopeType.PROJECT);
        
        if (currentRoles.isEmpty()) {
            throw new ApiException("User is not a member of this project");
        }
        
        // Aggiorna il ruolo (dovrebbe essere uno solo, ma per sicurezza iteriamo)
        for (UserRole role : currentRoles) {
            role.setRoleName(newRoleName);
            userRoleRepository.save(role);
        }
    }

    /**
     * Rimuove un membro da un progetto (elimina tutti i suoi ruoli PROJECT in quel progetto)
     * Nota: Non permette di rimuovere TENANT_ADMIN
     */
    public void removeProjectMember(Long projectId, Long userId, Tenant tenant) {
        // Verifica che il progetto esista e appartenga alla tenant
        projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        // Verifica che l'utente NON sia TENANT_ADMIN
        boolean isTenantAdmin = userRoleRepository.existsByUserIdAndTenantIdAndRoleName(
                userId, tenant.getId(), "ADMIN");
        
        if (isTenantAdmin) {
            throw new ApiException("Cannot remove TENANT_ADMIN users from projects. They have automatic access.");
        }
        
        // Verifica che l'utente sia effettivamente un membro del progetto
        boolean isMember = userRoleRepository.existsByUserIdAndProjectIdAndScope(
                userId, projectId, ScopeType.PROJECT);
        
        if (!isMember) {
            throw new ApiException("User is not a member of this project");
        }
        
        // Rimuovi tutti i ruoli PROJECT dell'utente per questo progetto
        userRoleRepository.deleteByUserIdAndProjectId(userId, projectId);
    }

    /**
     * Aggiunge un progetto ai preferiti dell'utente
     */
    public void addProjectToFavorites(Long projectId, User user, Tenant tenant) {
        // Verifica che il progetto esista e appartenga alla tenant
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        // Verifica se è già nei preferiti
        if (userFavoriteProjectRepository.existsByUserAndProjectAndTenant(
                user.getId(), projectId, tenant.getId())) {
            throw new ApiException("Project is already in favorites");
        }
        
        // Crea il preferito
        UserFavoriteProject favorite = UserFavoriteProject.builder()
                .user(user)
                .project(project)
                .tenant(tenant)
                .build();
        
        userFavoriteProjectRepository.save(favorite);
    }

    /**
     * Rimuove un progetto dai preferiti dell'utente
     */
    public void removeProjectFromFavorites(Long projectId, User user, Tenant tenant) {
        // Verifica che il progetto esista
        projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        // Verifica se è nei preferiti
        if (!userFavoriteProjectRepository.existsByUserAndProjectAndTenant(
                user.getId(), projectId, tenant.getId())) {
            throw new ApiException("Project is not in favorites");
        }
        
        // Rimuovi dai preferiti
        userFavoriteProjectRepository.deleteByUserIdAndProjectIdAndTenantId(
                user.getId(), projectId, tenant.getId());
    }

    /**
     * Ottiene tutti i progetti preferiti dell'utente in una tenant
     */
    @Transactional(readOnly = true)
    public List<ProjectViewDto> getFavoriteProjects(User user, Tenant tenant) {
        List<Project> favoriteProjects = userFavoriteProjectRepository
                .findFavoriteProjectsByUserAndTenant(user.getId(), tenant.getId());
        
        return dtoMapper.toProjectViewDtos(favoriteProjects);
    }

    /**
     * Verifica se un progetto è nei preferiti
     */
    @Transactional(readOnly = true)
    public boolean isProjectFavorite(Long projectId, User user, Tenant tenant) {
        return userFavoriteProjectRepository.existsByUserAndProjectAndTenant(
                user.getId(), projectId, tenant.getId());
    }

}

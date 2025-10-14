package com.example.demo.service;

import com.example.demo.dto.ProjectCreateDto;
import com.example.demo.dto.ProjectUpdateDto;
import com.example.demo.dto.ProjectViewDto;
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
 * 
 * TODO: I ruoli PROJECT-level (ADMIN a livello project) usano ancora il vecchio sistema
 * Grant/GrantRoleAssignment. Dovrebbero essere migrati a UserRole come fatto per i ruoli TENANT-level.
 * Per ora GrantRoleLookup ha metodi deprecati per mantenere la backward compatibility.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRoleRepository userRoleRepository;

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
        projectInitializers.forEach(init -> init.initialize(project, tenant));
        projectRepository.save(project);

        // TODO: I ruoli PROJECT-level dovrebbero usare UserRole invece di Grant/GrantRoleAssignment
        // Per ora manteniamo il vecchio sistema per i progetti, ma va refactorato
        
        // Assegna ruolo ADMIN a livello PROJECT usando UserRole (nuovo sistema)
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

        project.setItemTypeSet(itemTypeSet);
        projectRepository.save(project);
    }



}

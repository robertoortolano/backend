package com.example.demo.service;

import com.example.demo.dto.ProjectCreateDto;
import com.example.demo.dto.ProjectUpdateDto;
import com.example.demo.dto.ProjectViewDto;
import com.example.demo.entity.*;
import com.example.demo.enums.RoleName;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.initializer.ProjectInitializer;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.*;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;

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


    public ProjectViewDto createProjectForCurrentUser(ProjectCreateDto dto, User user, Tenant tenant) {

        boolean authorized = grantRoleLookup.existsByUserGlobal(user, tenant, RoleName.ADMIN);

        if (!authorized) {
            throw new ApiException("Accesso negato: non sei TENANT_ADMIN per questa tenant");
        }

        if (projectRepository.existsByProjectKeyAndTenantId(dto.key(), tenant.getId())) {
            throw new ApiException("Project key già esistente");
        }

        Project project = dtoMapper.toProject(dto);
        projectInitializers.forEach(init -> init.initialize(project, tenant));
        projectRepository.save(project);

        Role projectRole = grantRoleLookup.getRoleByNameAndScope(RoleName.ADMIN, ScopeType.PROJECT, tenant);

        // Crea il Grant relativo al ruolo (tenant è implicito in role)
        Grant newGrant = new Grant();
        newGrant.setRole(projectRole);
        newGrant.getUsers().add(user);
        grantRepository.save(newGrant);

        // Crea l’assegnazione tra grant, tenant e progetto
        GrantRoleAssignment newGra = new GrantRoleAssignment();
        newGra.setGrant(newGrant);
        newGra.setTenant(tenant);
        newGra.setProject(project);
        grantRoleAssignmentRepository.save(newGra);

        return dtoMapper.toProjectViewDto(project);
    }


    @Transactional(readOnly = true)
    public List<ProjectViewDto> getProjectsForUser(Tenant tenant, User user) {

        // Controllo se l'utente ha un ruolo globale da ADMIN sul tenant
        boolean isTenantAdmin = grantRoleLookup.existsByUserGlobal(user, tenant, RoleName.ADMIN);

        if (isTenantAdmin) {
            // Se è admin tenant, restituisco tutti i progetti della tenant
            return dtoMapper.toProjectViewDtos(projectRepository.findByTenant(tenant));
        }

        // Altrimenti restituisco solo i progetti dove ha ruolo di PROJECT_ADMIN
        return dtoMapper.toProjectViewDtos(grantRoleLookup.getProjectsByUserAndProjectRole(
                user, tenant, RoleName.ADMIN
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
        Project project = projectRepository.findByIdAndTenant(projectId, user.getActiveTenant())
                .orElseThrow(() -> new ApiException("Progetto non trovato"));

        ItemTypeSet itemTypeSet = itemTypeSetLookup.getById(tenant, setId);

        project.setItemTypeSet(itemTypeSet);
        projectRepository.save(project);
    }



}

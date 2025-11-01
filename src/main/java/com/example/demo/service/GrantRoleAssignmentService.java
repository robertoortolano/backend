package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class GrantRoleAssignmentService {

    private final GrantRoleAssignmentRepository grantRoleAssignmentRepository;
    private final GrantRepository grantRepository;
    private final RoleRepository roleRepository;
    private final ProjectRepository projectRepository;

    /**
     * Crea un GrantRoleAssignment PROJECT-level
     * Collega un Grant (con users/groups) a un Role template in un progetto specifico
     */
    public GrantRoleAssignment createProjectGrantRoleAssignment(
            Long grantId,
            Long roleId,
            Long projectId,
            Tenant tenant
    ) {
        // Verifica che il Grant esista e appartenga alla tenant
        Grant grant = grantRepository.findByIdAndTenant(grantId, tenant)
                .orElseThrow(() -> new ApiException("Grant not found"));

        // Verifica che il Role esista e appartenga alla tenant
        Role role = roleRepository.findByIdAndTenant(roleId, tenant)
                .orElseThrow(() -> new ApiException("Role not found"));

        // Verifica che il Project esista e appartenga alla tenant
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));

        // Verifica che non esista già questa assegnazione
        Optional<GrantRoleAssignment> existing = grantRoleAssignmentRepository.findByGrantAndTenantAndProject(
                grant, tenant, project);

        if (existing.isPresent()) {
            GrantRoleAssignment existingAssignment = existing.get();
            // Se il ruolo è diverso, aggiorna; altrimenti errore
            if (!existingAssignment.getRole().getId().equals(roleId)) {
                existingAssignment.setRole(role);
                return grantRoleAssignmentRepository.save(existingAssignment);
            } else {
                throw new ApiException("GrantRoleAssignment already exists for this Grant, Role and Project");
            }
        }

        // Crea nuovo GrantRoleAssignment
        GrantRoleAssignment assignment = GrantRoleAssignment.builder()
                .grant(grant)
                .role(role)
                .tenant(tenant)
                .project(project)
                .build();

        return grantRoleAssignmentRepository.save(assignment);
    }

    /**
     * Elimina un GrantRoleAssignment PROJECT-level
     */
    @Transactional
    public void deleteProjectGrantRoleAssignment(
            Long grantId,
            Long roleId,
            Long projectId,
            Tenant tenant
    ) {
        Grant grant = grantRepository.findByIdAndTenant(grantId, tenant)
                .orElseThrow(() -> new ApiException("Grant not found"));

        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));

        Optional<GrantRoleAssignment> assignment = grantRoleAssignmentRepository.findByGrantAndTenantAndProject(
                grant, tenant, project);

        if (assignment.isPresent()) {
            GrantRoleAssignment gra = assignment.get();
            // Verifica che il ruolo corrisponda
            if (gra.getRole().getId().equals(roleId)) {
                grantRoleAssignmentRepository.delete(gra);
            } else {
                throw new ApiException("GrantRoleAssignment not found with specified Role");
            }
        } else {
            throw new ApiException("GrantRoleAssignment not found");
        }
    }

    /**
     * Ottiene tutti i GrantRoleAssignment per un progetto
     */
    @Transactional(readOnly = true)
    public List<GrantRoleAssignment> getProjectGrantRoleAssignments(Long projectId, Tenant tenant) {
        // Verifica che il progetto esista
        projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));

        return grantRoleAssignmentRepository.findAll().stream()
                .filter(gra -> gra.getProject() != null && gra.getProject().getId().equals(projectId))
                .filter(gra -> gra.getTenant().getId().equals(tenant.getId()))
                .toList();
    }

    /**
     * Ottiene tutti i GrantRoleAssignment per un ruolo in un progetto
     */
    @Transactional(readOnly = true)
    public List<GrantRoleAssignment> getProjectGrantRoleAssignmentsByRole(
            Long projectId,
            Long roleId,
            Tenant tenant
    ) {
        // Verifica che il progetto e il ruolo esistano
        projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));

        roleRepository.findByIdAndTenant(roleId, tenant)
                .orElseThrow(() -> new ApiException("Role not found"));

        return grantRoleAssignmentRepository.findAll().stream()
                .filter(gra -> gra.getProject() != null && gra.getProject().getId().equals(projectId))
                .filter(gra -> gra.getRole().getId().equals(roleId))
                .filter(gra -> gra.getTenant().getId().equals(tenant.getId()))
                .toList();
    }
}


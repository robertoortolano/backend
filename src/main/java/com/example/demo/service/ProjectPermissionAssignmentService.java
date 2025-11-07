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
 * Servizio per gestire ProjectPermissionAssignment (assegnazioni di progetto per Permission).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ProjectPermissionAssignmentService {
    
    private final ProjectPermissionAssignmentRepository projectPermissionAssignmentRepository;
    private final PermissionAssignmentRepository permissionAssignmentRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectRepository projectRepository;
    private final ItemTypeSetRepository itemTypeSetRepository;
    private final RoleRepository roleRepository;
    private final GrantRepository grantRepository;
    private final GrantCleanupService grantCleanupService;
    
    /**
     * Crea o aggiorna un ProjectPermissionAssignment per una Permission e un progetto.
     * 
     * @param permissionType Tipo della Permission (es. "FieldOwnerPermission")
     * @param permissionId ID della Permission
     * @param projectId ID del progetto
     * @param itemTypeSetId ID dell'ItemTypeSet
     * @param roleIds Set di ID dei ruoli custom da assegnare (può essere null/empty)
     * @param grantId ID del Grant da assegnare (può essere null)
     * @param tenant Tenant di appartenenza
     * @return ProjectPermissionAssignment creato o aggiornato
     */
    public ProjectPermissionAssignment createOrUpdateProjectAssignment(
            String permissionType,
            Long permissionId,
            Long projectId,
            Long itemTypeSetId,
            Set<Long> roleIds,
            Long grantId,
            Tenant tenant) {
        
        // Verifica che il progetto esista
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        // Verifica che l'ItemTypeSet esista
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByIdAndTenant(itemTypeSetId, tenant)
                .orElseThrow(() -> new ApiException("ItemTypeSet not found"));
        
        // Trova o crea ProjectPermissionAssignment
        Optional<ProjectPermissionAssignment> existingOpt = projectPermissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndProjectAndTenant(permissionType, permissionId, project, tenant);
        
        ProjectPermissionAssignment projectAssignment;
        PermissionAssignment assignment;
        
        if (existingOpt.isPresent()) {
            projectAssignment = existingOpt.get();
            assignment = projectAssignment.getAssignment();
        } else {
            // Crea nuovo PermissionAssignment per il progetto
            assignment = PermissionAssignment.builder()
                    .permissionType(permissionType)
                    .permissionId(permissionId)
                    .tenant(tenant)
                    .roles(new HashSet<>())
                    .build();
            
            projectAssignment = ProjectPermissionAssignment.builder()
                    .permissionType(permissionType)
                    .permissionId(permissionId)
                    .project(project)
                    .itemTypeSet(itemTypeSet)
                    .tenant(tenant)
                    .assignment(assignment)
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
        
        assignment = permissionAssignmentRepository.save(assignment);
        projectAssignment.setAssignment(assignment);
        
        return projectPermissionAssignmentRepository.save(projectAssignment);
    }
    
    /**
     * Ottiene ProjectPermissionAssignment per una Permission e un progetto.
     * 
     * @param permissionType Tipo della Permission
     * @param permissionId ID della Permission
     * @param projectId ID del progetto
     * @param tenant Tenant di appartenenza
     * @return ProjectPermissionAssignment se esiste, altrimenti Optional.empty()
     */
    @Transactional(readOnly = true)
    public Optional<ProjectPermissionAssignment> getProjectAssignment(
            String permissionType,
            Long permissionId,
            Long projectId,
            Tenant tenant) {
        
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        return projectPermissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndProjectAndTenantWithCollections(permissionType, permissionId, project, tenant);
    }
    
    /**
     * Elimina ProjectPermissionAssignment per una Permission e un progetto.
     * Elimina anche il PermissionAssignment associato e il Grant se esiste.
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
        
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        Optional<ProjectPermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndProjectAndTenant(permissionType, permissionId, project, tenant);
        
        if (projectAssignmentOpt.isPresent()) {
            ProjectPermissionAssignment projectAssignment = projectAssignmentOpt.get();
            PermissionAssignment assignment = projectAssignment.getAssignment();
            
            // Elimina Grant se esiste
            if (assignment.getGrant() != null) {
                Long grantId = assignment.getGrant().getId();
                grantCleanupService.deleteGrantCompletely(grantId);
            }
            
            // Elimina PermissionAssignment
            permissionAssignmentRepository.delete(assignment);
            
            // Elimina ProjectPermissionAssignment
            projectPermissionAssignmentRepository.delete(projectAssignment);
        }
    }
    
    /**
     * Crea un Grant e lo assegna a ProjectPermissionAssignment.
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
     * @return ProjectPermissionAssignment aggiornato
     */
    public ProjectPermissionAssignment createAndAssignGrant(
            String permissionType,
            Long permissionId,
            Long projectId,
            Long itemTypeSetId,
            Set<Long> userIds,
            Set<Long> groupIds,
            Set<Long> negatedUserIds,
            Set<Long> negatedGroupIds,
            Tenant tenant) {
        
        // Usa il servizio base per creare/aggiornare l'assignment
        PermissionAssignment assignment = permissionAssignmentService.createAndAssignGrant(
                permissionType, permissionId, userIds, groupIds, negatedUserIds, negatedGroupIds, tenant);
        
        // Verifica che il progetto esista
        Project project = projectRepository.findByIdAndTenant(projectId, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
        
        // Verifica che l'ItemTypeSet esista
        ItemTypeSet itemTypeSet = itemTypeSetRepository.findByIdAndTenant(itemTypeSetId, tenant)
                .orElseThrow(() -> new ApiException("ItemTypeSet not found"));
        
        // Trova o crea ProjectPermissionAssignment
        Optional<ProjectPermissionAssignment> existingOpt = projectPermissionAssignmentRepository
                .findByPermissionTypeAndPermissionIdAndProjectAndTenant(permissionType, permissionId, project, tenant);
        
        ProjectPermissionAssignment projectAssignment;
        if (existingOpt.isPresent()) {
            projectAssignment = existingOpt.get();
            projectAssignment.setAssignment(assignment);
        } else {
            projectAssignment = ProjectPermissionAssignment.builder()
                    .permissionType(permissionType)
                    .permissionId(permissionId)
                    .project(project)
                    .itemTypeSet(itemTypeSet)
                    .tenant(tenant)
                    .assignment(assignment)
                    .build();
        }
        
        return projectPermissionAssignmentRepository.save(projectAssignment);
    }
    
    /**
     * Elimina tutte le ProjectPermissionAssignment per un ItemTypeSet.
     * Utile quando si elimina un ItemTypeSet.
     * 
     * @param itemTypeSetId ID dell'ItemTypeSet
     * @param tenantId ID del tenant
     */
    public void deleteByItemTypeSet(Long itemTypeSetId, Long tenantId) {
        var projectAssignments = projectPermissionAssignmentRepository
                .findByItemTypeSetIdAndTenantId(itemTypeSetId, tenantId);
        
        for (ProjectPermissionAssignment projectAssignment : projectAssignments) {
            PermissionAssignment assignment = projectAssignment.getAssignment();
            
            // Elimina Grant se esiste
            if (assignment.getGrant() != null) {
                Long grantId = assignment.getGrant().getId();
                grantCleanupService.deleteGrantCompletely(grantId);
            }
            
            // Elimina PermissionAssignment
            permissionAssignmentRepository.delete(assignment);
        }
        
        // Elimina ProjectPermissionAssignment
        projectPermissionAssignmentRepository.deleteByItemTypeSetIdAndTenantId(itemTypeSetId, tenantId);
    }
    
}


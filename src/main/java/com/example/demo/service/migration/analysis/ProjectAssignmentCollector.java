package com.example.demo.service.migration.analysis;

import com.example.demo.dto.ItemTypeConfigurationMigrationImpactDto;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.PermissionAssignment;
import com.example.demo.entity.Project;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.service.ProjectPermissionAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProjectAssignmentCollector {

    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;

    public ProjectAssignmentSummary collect(
            String permissionType,
            Long permissionId,
            Tenant tenant,
            ItemTypeSet itemTypeSet
    ) {
        List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrants = new ArrayList<>();

        if (itemTypeSet == null) {
            return new ProjectAssignmentSummary(projectGrants);
        }

        if (itemTypeSet.getProject() != null) {
            Optional<PermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                    permissionType,
                    permissionId,
                    itemTypeSet.getProject().getId(),
                    tenant
            );
            projectAssignmentOpt.ifPresent(assignment -> {
                // Raccogli ruoli e grant dalla PermissionAssignment di progetto
                List<String> projectRoles = assignment.getRoles() != null
                        ? assignment.getRoles().stream()
                                .map(Role::getName)
                                .collect(Collectors.toList())
                        : Collections.emptyList();
                
                Long projectGrantId = assignment.getGrant() != null ? assignment.getGrant().getId() : null;
                // Per i grant di progetto, popoliamo grantName solo se c'è un ruolo specifico associato
                // Non usiamo "Grant globale" perché questi sono grant di progetto, non globali
                String projectGrantName = assignment.getGrant() != null && assignment.getGrant().getRole() != null
                        ? assignment.getGrant().getRole().getName()
                        : null;
                
                // Aggiungi solo se ci sono ruoli o grant
                if (!projectRoles.isEmpty() || projectGrantId != null) {
                    projectGrants.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                            .projectId(itemTypeSet.getProject().getId())
                            .projectName(itemTypeSet.getProject().getName())
                            .assignedRoles(projectRoles)
                            .grantId(projectGrantId)
                            .grantName(projectGrantName)
                            .build());
                }
            });
        } else if (itemTypeSet.getProjectsAssociation() != null) {
            for (Project project : itemTypeSet.getProjectsAssociation()) {
                Optional<PermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                        permissionType,
                        permissionId,
                        project.getId(),
                        tenant
                );
                projectAssignmentOpt.ifPresent(assignment -> {
                    // Raccogli ruoli e grant dalla PermissionAssignment di progetto
                    List<String> projectRoles = assignment.getRoles() != null
                            ? assignment.getRoles().stream()
                                    .map(Role::getName)
                                    .collect(Collectors.toList())
                            : Collections.emptyList();
                    
                    Long projectGrantId = assignment.getGrant() != null ? assignment.getGrant().getId() : null;
                    // Per i grant di progetto, popoliamo grantName solo se c'è un ruolo specifico associato
                    // Non usiamo "Grant globale" perché questi sono grant di progetto, non globali
                    String projectGrantName = assignment.getGrant() != null && assignment.getGrant().getRole() != null
                            ? assignment.getGrant().getRole().getName()
                            : null;
                    
                    // Aggiungi solo se ci sono ruoli o grant
                    if (!projectRoles.isEmpty() || projectGrantId != null) {
                        projectGrants.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                .projectId(project.getId())
                                .projectName(project.getName())
                                .assignedRoles(projectRoles)
                                .grantId(projectGrantId)
                                .grantName(projectGrantName)
                                .build());
                    }
                });
            }
        }

        return new ProjectAssignmentSummary(projectGrants);
    }

    public record ProjectAssignmentSummary(
            List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrants
    ) {
        public boolean hasProjectRoles() {
            return projectGrants.stream()
                    .anyMatch(pg -> pg.getAssignedRoles() != null && !pg.getAssignedRoles().isEmpty());
        }
    }
}





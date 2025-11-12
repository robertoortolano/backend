package com.example.demo.service.migration.analysis;

import com.example.demo.dto.ItemTypeConfigurationMigrationImpactDto;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.PermissionAssignment;
import com.example.demo.entity.Project;
import com.example.demo.entity.Tenant;
import com.example.demo.service.ProjectPermissionAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        boolean hasProjectRoles = false;

        if (itemTypeSet == null) {
            return new ProjectAssignmentSummary(projectGrants, hasProjectRoles);
        }

        if (itemTypeSet.getProject() != null) {
            Optional<PermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                    permissionType,
                    permissionId,
                    itemTypeSet.getProject().getId(),
                    tenant
            );
            if (projectAssignmentOpt.isPresent()) {
                PermissionAssignment assignment = projectAssignmentOpt.get();
                if (assignment.getGrant() != null) {
                    projectGrants.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                            .projectId(itemTypeSet.getProject().getId())
                            .projectName(itemTypeSet.getProject().getName())
                            .build());
                }
                if (assignment.getRoles() != null && !assignment.getRoles().isEmpty()) {
                    hasProjectRoles = true;
                }
            }
        } else if (itemTypeSet.getProjectsAssociation() != null) {
            for (Project project : itemTypeSet.getProjectsAssociation()) {
                Optional<PermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                        permissionType,
                        permissionId,
                        project.getId(),
                        tenant
                );
                if (projectAssignmentOpt.isPresent()) {
                    PermissionAssignment assignment = projectAssignmentOpt.get();
                    if (assignment.getGrant() != null) {
                        projectGrants.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
                                .projectId(project.getId())
                                .projectName(project.getName())
                                .build());
                    }
                    if (assignment.getRoles() != null && !assignment.getRoles().isEmpty()) {
                        hasProjectRoles = true;
                    }
                }
            }
        }

        return new ProjectAssignmentSummary(projectGrants, hasProjectRoles);
    }

    public record ProjectAssignmentSummary(
            List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrants,
            boolean hasProjectRoles
    ) {
    }
}



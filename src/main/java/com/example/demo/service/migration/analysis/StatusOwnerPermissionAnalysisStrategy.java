package com.example.demo.service.migration.analysis;

import com.example.demo.dto.ItemTypeConfigurationMigrationImpactDto;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.PermissionAssignment;
import com.example.demo.entity.Project;
import com.example.demo.entity.Role;
import com.example.demo.entity.Status;
import com.example.demo.entity.StatusOwnerPermission;
import com.example.demo.entity.Tenant;
import com.example.demo.repository.StatusOwnerPermissionRepository;
import com.example.demo.service.PermissionAssignmentService;
import com.example.demo.service.ProjectPermissionAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class StatusOwnerPermissionAnalysisStrategy {

    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectPermissionAssignmentService projectPermissionAssignmentService;

    public List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyze(MigrationAnalysisContext context) {
        if (!context.workflowChanged()) {
            return Collections.emptyList();
        }

        ItemTypeConfiguration configuration = context.configuration();
        List<StatusOwnerPermission> existingPermissions = statusOwnerPermissionRepository.findAllByItemTypeConfiguration(configuration);
        if (existingPermissions.isEmpty()) {
            return Collections.emptyList();
        }

        ItemTypeConfigurationMigrationImpactDto.WorkflowInfo newWorkflowInfo = context.newWorkflowInfo();
        Set<Long> newStatusIds = newWorkflowInfo.getWorkflowStatuses().stream()
                .map(ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo::getStatusId)
                .collect(Collectors.toSet());
        Map<Long, ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo> newStatusesMap = newWorkflowInfo.getWorkflowStatuses().stream()
                .collect(Collectors.toMap(
                        ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo::getStatusId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        Tenant tenant = configuration.getTenant();
        Long itemTypeSetId = context.itemTypeSetId();
        ItemTypeSet itemTypeSet = context.owningItemTypeSet();

        // Filtra solo le permission che sono effettivamente impattate:
        // - Status non esiste più nel nuovo workflow
        return existingPermissions.stream()
                .filter(permission -> {
                    Long statusId = permission.getWorkflowStatus() != null && permission.getWorkflowStatus().getStatus() != null
                            ? permission.getWorkflowStatus().getStatus().getId() : null;
                    // Include solo se lo status non esiste più nel nuovo workflow
                    return statusId == null || !newStatusIds.contains(statusId);
                })
                .map(permission -> buildImpact(permission, context, tenant, itemTypeSet, itemTypeSetId, newStatusIds, newStatusesMap))
                .collect(Collectors.toList());
    }

    private ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact buildImpact(
            StatusOwnerPermission permission,
            MigrationAnalysisContext context,
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            Long itemTypeSetId,
            Set<Long> newStatusIds,
            Map<Long, ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo> newStatusesMap
    ) {
        Status status = permission.getWorkflowStatus().getStatus();
        Long statusId = status.getId();
        String statusName = status.getName();

        boolean canPreserve = newStatusIds.contains(statusId);
        ItemTypeConfigurationMigrationImpactDto.WorkflowStatusInfo matchingStatus = canPreserve ? newStatusesMap.get(statusId) : null;

        Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment("StatusOwnerPermission", permission.getId(), tenant);
        List<String> assignedRoles = assignmentOpt.map(a -> a.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toList()))
                .orElseGet(ArrayList::new);

        Long grantId = null;
        String grantName = null;
        if (assignmentOpt.isPresent() && assignmentOpt.get().getGrant() != null) {
            grantId = assignmentOpt.get().getGrant().getId();
            grantName = assignmentOpt.get().getGrant().getRole() != null
                    ? assignmentOpt.get().getGrant().getRole().getName()
                    : "Grant globale";
        }

        List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> projectGrants = collectProjectGrants(
                permission,
                tenant,
                itemTypeSet
        );

        // Verifica se ci sono assegnazioni: ruoli globali, grant globale, o assegnazioni di progetto (ruoli o grant)
        boolean hasProjectAssignments = projectGrants.stream()
                .anyMatch(pg -> (pg.getAssignedRoles() != null && !pg.getAssignedRoles().isEmpty()) || pg.getGrantId() != null);
        boolean hasAssignments = !assignedRoles.isEmpty() || grantId != null || hasProjectAssignments;
        boolean defaultPreserve = canPreserve && hasAssignments;

        return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                .permissionId(permission.getId())
                .permissionType("STATUS_OWNERS")
                .entityId(statusId)
                .entityName(statusName)
                .matchingEntityId(canPreserve && matchingStatus != null ? matchingStatus.getStatusId() : null)
                .matchingEntityName(canPreserve && matchingStatus != null ? matchingStatus.getStatusName() : null)
                .assignedRoles(assignedRoles)
                .hasAssignments(hasAssignments)
                .canBePreserved(canPreserve)
                .defaultPreserve(defaultPreserve)
                .suggestedAction(canPreserve ? "PRESERVE" : "REMOVE")
                .itemTypeSetId(itemTypeSetId)
                .itemTypeSetName(context.itemTypeSetName())
                .projectId(itemTypeSet != null && itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                .projectName(itemTypeSet != null && itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                .grantId(grantId)
                .grantName(grantName)
                .projectGrants(projectGrants)
                .build();
    }

    private List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> collectProjectGrants(
            StatusOwnerPermission permission,
            Tenant tenant,
            ItemTypeSet itemTypeSet
    ) {
        if (itemTypeSet == null) {
            return Collections.emptyList();
        }

        List<ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo> grants = new ArrayList<>();

        if (itemTypeSet.getProject() != null) {
            Optional<PermissionAssignment> projectAssignmentOpt = projectPermissionAssignmentService.getProjectAssignment(
                    "StatusOwnerPermission",
                    permission.getId(),
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
                    grants.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
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
                        "StatusOwnerPermission",
                        permission.getId(),
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
                        grants.add(ItemTypeConfigurationMigrationImpactDto.ProjectGrantInfo.builder()
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

        return grants;
    }
}


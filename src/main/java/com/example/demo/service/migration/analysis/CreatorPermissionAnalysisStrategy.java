package com.example.demo.service.migration.analysis;

import com.example.demo.dto.ItemTypeConfigurationMigrationImpactDto;
import com.example.demo.entity.CreatorPermission;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.PermissionAssignment;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.repository.CreatorPermissionRepository;
import com.example.demo.service.PermissionAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CreatorPermissionAnalysisStrategy {

    private static final String PERMISSION_TYPE = "CreatorPermission";

    private final CreatorPermissionRepository creatorPermissionRepository;
    private final PermissionAssignmentService permissionAssignmentService;
    private final ProjectAssignmentCollector projectAssignmentCollector;

    public List<ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact> analyze(MigrationAnalysisContext context) {
        ItemTypeConfiguration configuration = context.configuration();
        List<CreatorPermission> existingPermissions = creatorPermissionRepository.findAllByItemTypeConfiguration(configuration);
        if (existingPermissions.isEmpty()) {
            return Collections.emptyList();
        }

        Tenant tenant = configuration.getTenant();
        ItemTypeSet itemTypeSet = context.owningItemTypeSet();
        Long itemTypeSetId = context.itemTypeSetId();
        String itemTypeSetName = context.itemTypeSetName();

        return existingPermissions.stream()
                .map(permission -> buildImpact(permission, configuration, tenant, itemTypeSet, itemTypeSetId, itemTypeSetName))
                .collect(Collectors.toList());
    }

    private ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact buildImpact(
            CreatorPermission permission,
            ItemTypeConfiguration configuration,
            Tenant tenant,
            ItemTypeSet itemTypeSet,
            Long itemTypeSetId,
            String itemTypeSetName
    ) {
        Optional<PermissionAssignment> assignmentOpt = permissionAssignmentService.getAssignment(PERMISSION_TYPE, permission.getId(), tenant);
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

        ProjectAssignmentCollector.ProjectAssignmentSummary projectSummary = projectAssignmentCollector.collect(
                PERMISSION_TYPE,
                permission.getId(),
                tenant,
                itemTypeSet
        );

        // Verifica se ci sono assegnazioni: ruoli globali, grant globale, o assegnazioni di progetto (ruoli o grant)
        boolean hasProjectAssignments = projectSummary.projectGrants().stream()
                .anyMatch(pg -> (pg.getAssignedRoles() != null && !pg.getAssignedRoles().isEmpty()) || pg.getGrantId() != null);
        boolean hasAssignments = !assignedRoles.isEmpty() || grantId != null || hasProjectAssignments;

        return ItemTypeConfigurationMigrationImpactDto.SelectablePermissionImpact.builder()
                .permissionId(permission.getId())
                .permissionType("CREATORS")
                .entityId(configuration.getItemType() != null ? configuration.getItemType().getId() : null)
                .entityName(configuration.getItemType() != null ? configuration.getItemType().getName() : null)
                .matchingEntityId(configuration.getItemType() != null ? configuration.getItemType().getId() : null)
                .matchingEntityName(configuration.getItemType() != null ? configuration.getItemType().getName() : null)
                .assignedRoles(assignedRoles)
                .hasAssignments(hasAssignments)
                .canBePreserved(true)
                .defaultPreserve(hasAssignments)
                .suggestedAction("PRESERVE")
                .itemTypeSetId(itemTypeSetId)
                .itemTypeSetName(itemTypeSetName)
                .projectId(configuration.getProject() != null ? configuration.getProject().getId() : null)
                .projectName(configuration.getProject() != null ? configuration.getProject().getName() : null)
                .grantId(grantId)
                .grantName(grantName)
                .projectGrants(projectSummary.projectGrants())
                .build();
    }
}





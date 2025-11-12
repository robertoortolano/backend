package com.example.demo.service.workflowimpact;

import com.example.demo.dto.TransitionRemovalImpactDto;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.service.workflowimpact.model.ExecutorPermissionImpactData;
import com.example.demo.service.workflowimpact.model.ProjectGrantData;
import com.example.demo.service.workflowimpact.model.TransitionImpactAnalysisResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class TransitionImpactResponseMapper {

    public TransitionRemovalImpactDto toDto(TransitionImpactAnalysisResult analysisResult) {
        List<TransitionRemovalImpactDto.PermissionImpact> executorPermissions = analysisResult.getExecutorPermissionImpacts()
                .stream()
                .map(this::mapExecutorPermission)
                .collect(Collectors.toList());

        List<TransitionRemovalImpactDto.ItemTypeSetImpact> affectedItemTypeSets = analysisResult.getAffectedItemTypeSets()
                .stream()
                .map(this::mapItemTypeSetImpact)
                .collect(Collectors.toList());

        int totalExecutorPermissions = executorPermissions.size();
        int totalRoleAssignments = analysisResult.getExecutorPermissionImpacts().stream()
                .mapToInt(impact -> impact.getAssignedRoles() != null ? impact.getAssignedRoles().size() : 0)
                .sum();
        int totalGrantAssignments = (int) analysisResult.getExecutorPermissionImpacts().stream()
                .filter(impact -> impact.getGrantId() != null)
                .count();

        return TransitionRemovalImpactDto.builder()
                .workflowId(analysisResult.getWorkflow().getId())
                .workflowName(analysisResult.getWorkflow().getName())
                .removedTransitionIds(List.copyOf(analysisResult.getRemovedTransitionIds()))
                .removedTransitionNames(List.copyOf(analysisResult.getRemovedTransitionNames()))
                .affectedItemTypeSets(affectedItemTypeSets)
                .executorPermissions(executorPermissions)
                // La rimozione di transizioni impatta solo le ExecutorPermission
                // StatusOwnerPermission e FieldStatusPermission non sono impattate
                .statusOwnerPermissions(List.of())
                .fieldStatusPermissions(List.of())
                .totalAffectedItemTypeSets(affectedItemTypeSets.size())
                .totalExecutorPermissions(totalExecutorPermissions)
                .totalStatusOwnerPermissions(0)
                .totalFieldStatusPermissions(0)
                .totalGrantAssignments(totalGrantAssignments)
                .totalRoleAssignments(totalRoleAssignments)
                .build();
    }

    private TransitionRemovalImpactDto.PermissionImpact mapExecutorPermission(ExecutorPermissionImpactData data) {
        return TransitionRemovalImpactDto.PermissionImpact.builder()
                .permissionId(data.getPermissionId())
                .permissionType(data.getPermissionType())
                .itemTypeSetId(data.getItemTypeSetId())
                .itemTypeSetName(data.getItemTypeSetName())
                .projectId(data.getProjectId())
                .projectName(data.getProjectName())
                .transitionId(data.getTransitionId())
                .transitionName(data.getTransitionName())
                .fromStatusName(data.getFromStatusName())
                .toStatusName(data.getToStatusName())
                .grantId(data.getGrantId())
                .grantName(data.getGrantName())
                .assignedRoles(data.getAssignedRoles())
                .hasAssignments(data.isHasAssignments())
                .transitionIdMatch(data.getTransitionIdMatch())
                .transitionNameMatch(data.getTransitionNameMatch())
                .canBePreserved(data.isCanBePreserved())
                .defaultPreserve(data.isDefaultPreserve())
                .projectGrants(mapProjectGrants(data.getProjectGrants()))
                .build();
    }

    private TransitionRemovalImpactDto.ItemTypeSetImpact mapItemTypeSetImpact(ItemTypeSet itemTypeSet) {
        return TransitionRemovalImpactDto.ItemTypeSetImpact.builder()
                .itemTypeSetId(itemTypeSet.getId())
                .itemTypeSetName(itemTypeSet.getName())
                .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                .build();
    }

    private List<TransitionRemovalImpactDto.ProjectGrantInfo> mapProjectGrants(List<ProjectGrantData> projectGrants) {
        if (projectGrants == null) {
            return null;
        }

        return projectGrants.stream()
                .filter(Objects::nonNull)
                .map(grant -> TransitionRemovalImpactDto.ProjectGrantInfo.builder()
                        .projectId(grant.getProjectId())
                        .projectName(grant.getProjectName())
                        .build())
                .collect(Collectors.toList());
    }
}



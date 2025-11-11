package com.example.demo.service.workflowimpact;

import com.example.demo.dto.StatusRemovalImpactDto;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.service.workflowimpact.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class StatusImpactResponseMapper {

    public StatusRemovalImpactDto toDto(StatusImpactAnalysisResult analysisResult) {
        List<StatusRemovalImpactDto.PermissionImpact> statusOwnerPermissions = analysisResult.getStatusOwnerPermissionImpacts()
                .stream()
                .map(this::mapStatusOwnerPermission)
                .collect(Collectors.toList());

        List<StatusRemovalImpactDto.ExecutorPermissionImpact> executorPermissions = analysisResult.getExecutorPermissionImpacts()
                .stream()
                .map(this::mapExecutorPermission)
                .collect(Collectors.toList());

        List<StatusRemovalImpactDto.FieldStatusPermissionImpact> fieldStatusPermissions = analysisResult.getFieldStatusPermissionImpacts()
                .stream()
                .map(this::mapFieldStatusPermission)
                .collect(Collectors.toList());

        List<StatusRemovalImpactDto.ItemTypeSetImpact> affectedItemTypeSets = analysisResult.getAffectedItemTypeSets()
                .stream()
                .map(this::mapItemTypeSetImpact)
                .collect(Collectors.toList());

        int totalStatusOwnerPermissions = statusOwnerPermissions.size();
        int totalExecutorPermissions = executorPermissions.size();
        int totalFieldStatusPermissions = fieldStatusPermissions.size();

        int totalRoleAssignments =
                analysisResult.getStatusOwnerPermissionImpacts().stream()
                        .mapToInt(impact -> impact.getAssignedRoles() != null ? impact.getAssignedRoles().size() : 0)
                        .sum()
                        + analysisResult.getExecutorPermissionImpacts().stream()
                        .mapToInt(impact -> impact.getAssignedRoles() != null ? impact.getAssignedRoles().size() : 0)
                        .sum()
                        + analysisResult.getFieldStatusPermissionImpacts().stream()
                        .mapToInt(impact -> impact.getAssignedRoles() != null ? impact.getAssignedRoles().size() : 0)
                        .sum();

        int totalGrantAssignments =
                (int) analysisResult.getStatusOwnerPermissionImpacts().stream()
                        .filter(impact -> impact.getGrantId() != null)
                        .count()
                        + (int) analysisResult.getExecutorPermissionImpacts().stream()
                        .filter(impact -> impact.getGrantId() != null)
                        .count()
                        + (int) analysisResult.getFieldStatusPermissionImpacts().stream()
                        .filter(impact -> impact.getGrantId() != null)
                        .count();

        return StatusRemovalImpactDto.builder()
                .workflowId(analysisResult.getWorkflow().getId())
                .workflowName(analysisResult.getWorkflow().getName())
                .removedStatusIds(List.copyOf(analysisResult.getRemovedStatusIds()))
                .removedStatusNames(List.copyOf(analysisResult.getRemovedStatusNames()))
                .removedTransitionIds(List.copyOf(analysisResult.getRemovedTransitionIds()))
                .removedTransitionNames(List.copyOf(analysisResult.getRemovedTransitionNames()))
                .affectedItemTypeSets(affectedItemTypeSets)
                .statusOwnerPermissions(statusOwnerPermissions)
                .executorPermissions(executorPermissions)
                .fieldStatusPermissions(fieldStatusPermissions)
                .totalAffectedItemTypeSets(affectedItemTypeSets.size())
                .totalStatusOwnerPermissions(totalStatusOwnerPermissions)
                .totalExecutorPermissions(totalExecutorPermissions)
                .totalFieldStatusPermissions(totalFieldStatusPermissions)
                .totalGrantAssignments(totalGrantAssignments)
                .totalRoleAssignments(totalRoleAssignments)
                .build();
    }

    private StatusRemovalImpactDto.PermissionImpact mapStatusOwnerPermission(StatusOwnerPermissionImpactData data) {
        return StatusRemovalImpactDto.PermissionImpact.builder()
                .permissionId(data.getPermissionId())
                .permissionType(data.getPermissionType())
                .itemTypeSetId(data.getItemTypeSetId())
                .itemTypeSetName(data.getItemTypeSetName())
                .projectId(data.getProjectId())
                .projectName(data.getProjectName())
                .workflowStatusId(data.getWorkflowStatusId())
                .workflowStatusName(data.getWorkflowStatusName())
                .statusName(data.getStatusName())
                .statusCategory(data.getStatusCategory())
                .grantId(data.getGrantId())
                .grantName(data.getGrantName())
                .assignedRoles(data.getAssignedRoles())
                .hasAssignments(data.isHasAssignments())
                .canBePreserved(data.isCanBePreserved())
                .defaultPreserve(data.isDefaultPreserve())
                .projectGrants(mapProjectGrants(data.getProjectGrants()))
                .build();
    }

    private StatusRemovalImpactDto.ExecutorPermissionImpact mapExecutorPermission(ExecutorPermissionImpactData data) {
        return StatusRemovalImpactDto.ExecutorPermissionImpact.builder()
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

    private StatusRemovalImpactDto.FieldStatusPermissionImpact mapFieldStatusPermission(FieldStatusPermissionImpactData data) {
        return StatusRemovalImpactDto.FieldStatusPermissionImpact.builder()
                .permissionId(data.getPermissionId())
                .permissionType(data.getPermissionType())
                .itemTypeSetId(data.getItemTypeSetId())
                .itemTypeSetName(data.getItemTypeSetName())
                .projectId(data.getProjectId())
                .projectName(data.getProjectName())
                .fieldId(data.getFieldId())
                .fieldName(data.getFieldName())
                .workflowStatusId(data.getWorkflowStatusId())
                .workflowStatusName(data.getWorkflowStatusName())
                .statusName(data.getStatusName())
                .grantId(data.getGrantId())
                .grantName(data.getGrantName())
                .assignedRoles(data.getAssignedRoles())
                .hasAssignments(data.isHasAssignments())
                .canBePreserved(data.isCanBePreserved())
                .defaultPreserve(data.isDefaultPreserve())
                .projectGrants(mapProjectGrants(data.getProjectGrants()))
                .build();
    }

    private StatusRemovalImpactDto.ItemTypeSetImpact mapItemTypeSetImpact(ItemTypeSet itemTypeSet) {
        return StatusRemovalImpactDto.ItemTypeSetImpact.builder()
                .itemTypeSetId(itemTypeSet.getId())
                .itemTypeSetName(itemTypeSet.getName())
                .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                .build();
    }

    private List<StatusRemovalImpactDto.ProjectGrantInfo> mapProjectGrants(List<ProjectGrantData> projectGrants) {
        if (projectGrants == null) {
            return null;
        }

        return projectGrants.stream()
                .filter(Objects::nonNull)
                .map(grant -> StatusRemovalImpactDto.ProjectGrantInfo.builder()
                        .projectId(grant.getProjectId())
                        .projectName(grant.getProjectName())
                        .build())
                .collect(Collectors.toList());
    }
}


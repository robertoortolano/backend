package com.example.demo.service.workflowimpact;

import com.example.demo.dto.StatusRemovalImpactDto;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Project;
import com.example.demo.service.workflowimpact.model.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        List<StatusRemovalImpactDto.ItemTypeSetImpact> affectedItemTypeSets = mapItemTypeSetImpactsWithAggregates(
                analysisResult.getAffectedItemTypeSets(),
                statusOwnerPermissions,
                executorPermissions,
                fieldStatusPermissions);

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

    private List<StatusRemovalImpactDto.ItemTypeSetImpact> mapItemTypeSetImpactsWithAggregates(
            List<ItemTypeSet> itemTypeSets,
            List<StatusRemovalImpactDto.PermissionImpact> statusOwnerPermissions,
            List<StatusRemovalImpactDto.ExecutorPermissionImpact> executorPermissions,
            List<StatusRemovalImpactDto.FieldStatusPermissionImpact> fieldStatusPermissions
    ) {
        return itemTypeSets.stream()
                .map(its -> {
                    Long itemTypeSetId = its.getId();
                    
                    // Filtra permission per questo ItemTypeSet
                    List<StatusRemovalImpactDto.PermissionImpact> itsStatusOwnerPermissions = statusOwnerPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList());
                    
                    List<StatusRemovalImpactDto.ExecutorPermissionImpact> itsExecutorPermissions = executorPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList());
                    
                    List<StatusRemovalImpactDto.FieldStatusPermissionImpact> itsFieldStatusPermissions = fieldStatusPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList());
                    
                    // Calcola totali
                    int totalPermissions = itsStatusOwnerPermissions.size() + itsExecutorPermissions.size() + itsFieldStatusPermissions.size();
                    
                    // Calcola totalRoleAssignments: ruoli globali + ruoli di progetto
                    int totalGlobalRoles = itsStatusOwnerPermissions.stream()
                            .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                            .sum() + itsExecutorPermissions.stream()
                            .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                            .sum() + itsFieldStatusPermissions.stream()
                            .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                            .sum();
                    
                    int totalProjectRoles = itsStatusOwnerPermissions.stream()
                            .mapToInt(p -> p.getProjectAssignedRoles() != null 
                                    ? p.getProjectAssignedRoles().stream()
                                            .mapToInt(pr -> pr.getRoles() != null ? pr.getRoles().size() : 0)
                                            .sum()
                                    : 0)
                            .sum() + itsExecutorPermissions.stream()
                            .mapToInt(p -> p.getProjectAssignedRoles() != null 
                                    ? p.getProjectAssignedRoles().stream()
                                            .mapToInt(pr -> pr.getRoles() != null ? pr.getRoles().size() : 0)
                                            .sum()
                                    : 0)
                            .sum() + itsFieldStatusPermissions.stream()
                            .mapToInt(p -> p.getProjectAssignedRoles() != null 
                                    ? p.getProjectAssignedRoles().stream()
                                            .mapToInt(pr -> pr.getRoles() != null ? pr.getRoles().size() : 0)
                                            .sum()
                                    : 0)
                            .sum();
                    
                    int totalRoleAssignments = totalGlobalRoles + totalProjectRoles;
                    
                    // Calcola grant globali (conteggio di permission con grantId != null)
                    int totalGlobalGrants = (int) itsStatusOwnerPermissions.stream()
                            .filter(p -> p.getGrantId() != null)
                            .count() + (int) itsExecutorPermissions.stream()
                            .filter(p -> p.getGrantId() != null)
                            .count() + (int) itsFieldStatusPermissions.stream()
                            .filter(p -> p.getGrantId() != null)
                            .count();
                    
                    // Calcola grant di progetto per questo ItemTypeSet
                    Map<Long, Integer> projectGrantsCount = new HashMap<>();
                    
                    // Da StatusOwnerPermissions
                    for (StatusRemovalImpactDto.PermissionImpact perm : itsStatusOwnerPermissions) {
                        if (perm.getProjectGrants() != null) {
                            for (StatusRemovalImpactDto.ProjectGrantInfo pg : perm.getProjectGrants()) {
                                if (pg.getProjectId() != null) {
                                    projectGrantsCount.merge(pg.getProjectId(), 1, Integer::sum);
                                }
                            }
                        }
                    }
                    
                    // Da ExecutorPermissions
                    for (StatusRemovalImpactDto.ExecutorPermissionImpact perm : itsExecutorPermissions) {
                        if (perm.getProjectGrants() != null) {
                            for (StatusRemovalImpactDto.ProjectGrantInfo pg : perm.getProjectGrants()) {
                                if (pg.getProjectId() != null) {
                                    projectGrantsCount.merge(pg.getProjectId(), 1, Integer::sum);
                                }
                            }
                        }
                    }
                    
                    // Da FieldStatusPermissions
                    for (StatusRemovalImpactDto.FieldStatusPermissionImpact perm : itsFieldStatusPermissions) {
                        if (perm.getProjectGrants() != null) {
                            for (StatusRemovalImpactDto.ProjectGrantInfo pg : perm.getProjectGrants()) {
                                if (pg.getProjectId() != null) {
                                    projectGrantsCount.merge(pg.getProjectId(), 1, Integer::sum);
                                }
                            }
                        }
                    }
                    
                    int totalProjectGrants = projectGrantsCount.values().stream().mapToInt(Integer::intValue).sum();
                    
                    // Crea projectImpacts
                    List<StatusRemovalImpactDto.ProjectImpact> projectImpacts = projectGrantsCount.entrySet().stream()
                            .map(e -> {
                                // Trova il nome del progetto
                                String projectName = its.getProject() != null && its.getProject().getId().equals(e.getKey())
                                        ? its.getProject().getName()
                                        : its.getProjectsAssociation().stream()
                                                .filter(p -> p.getId().equals(e.getKey()))
                                                .findFirst()
                                                .map(com.example.demo.entity.Project::getName)
                                                .orElse("Progetto " + e.getKey());
                                return StatusRemovalImpactDto.ProjectImpact.builder()
                                        .projectId(e.getKey())
                                        .projectName(projectName)
                                        .projectGrantsCount(e.getValue())
                                        .build();
                            })
                            .collect(Collectors.toList());
                    
                    return StatusRemovalImpactDto.ItemTypeSetImpact.builder()
                            .itemTypeSetId(itemTypeSetId)
                            .itemTypeSetName(its.getName())
                            .projectId(its.getProject() != null ? its.getProject().getId() : null)
                            .projectName(its.getProject() != null ? its.getProject().getName() : null)
                            .totalPermissions(totalPermissions)
                            .totalRoleAssignments(totalRoleAssignments)
                            .totalGlobalGrants(totalGlobalGrants)
                            .totalProjectGrants(totalProjectGrants)
                            .projectImpacts(projectImpacts)
                            .build();
                })
                .collect(Collectors.toList());
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








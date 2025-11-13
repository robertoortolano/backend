package com.example.demo.service.workflowimpact;

import com.example.demo.dto.TransitionRemovalImpactDto;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.service.workflowimpact.model.ExecutorPermissionImpactData;
import com.example.demo.service.workflowimpact.model.ProjectGrantData;
import com.example.demo.service.workflowimpact.model.TransitionImpactAnalysisResult;
import org.springframework.stereotype.Component;

import com.example.demo.entity.Project;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class TransitionImpactResponseMapper {

    public TransitionRemovalImpactDto toDto(TransitionImpactAnalysisResult analysisResult) {
        List<TransitionRemovalImpactDto.PermissionImpact> executorPermissions = analysisResult.getExecutorPermissionImpacts()
                .stream()
                .map(this::mapExecutorPermission)
                .collect(Collectors.toList());

        List<TransitionRemovalImpactDto.ItemTypeSetImpact> affectedItemTypeSets = mapItemTypeSetImpactsWithAggregates(
                analysisResult.getAffectedItemTypeSets(),
                executorPermissions,
                List.of(), // statusOwnerPermissions - non ancora supportato da TransitionImpactAnalyzer
                List.of()  // fieldStatusPermissions - non ancora supportato da TransitionImpactAnalyzer
        );

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

    private List<TransitionRemovalImpactDto.ItemTypeSetImpact> mapItemTypeSetImpactsWithAggregates(
            List<ItemTypeSet> itemTypeSets,
            List<TransitionRemovalImpactDto.PermissionImpact> executorPermissions,
            List<TransitionRemovalImpactDto.StatusOwnerPermissionImpact> statusOwnerPermissions,
            List<TransitionRemovalImpactDto.FieldStatusPermissionImpact> fieldStatusPermissions
    ) {
        return itemTypeSets.stream()
                .map(its -> {
                    Long itemTypeSetId = its.getId();

                    List<TransitionRemovalImpactDto.PermissionImpact> itsExecutorPermissions = executorPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList());

                    List<TransitionRemovalImpactDto.StatusOwnerPermissionImpact> itsStatusOwnerPermissions = statusOwnerPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList());

                    List<TransitionRemovalImpactDto.FieldStatusPermissionImpact> itsFieldStatusPermissions = fieldStatusPermissions.stream()
                            .filter(p -> p.getItemTypeSetId().equals(itemTypeSetId))
                            .collect(Collectors.toList());

                    int totalPermissions = itsExecutorPermissions.size() + itsStatusOwnerPermissions.size() + itsFieldStatusPermissions.size();

                    int totalRoleAssignments = itsExecutorPermissions.stream()
                            .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                            .sum()
                            + itsStatusOwnerPermissions.stream()
                            .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                            .sum()
                            + itsFieldStatusPermissions.stream()
                            .mapToInt(p -> p.getAssignedRoles() != null ? p.getAssignedRoles().size() : 0)
                            .sum();

                    int totalGlobalGrants = (int) itsExecutorPermissions.stream()
                            .filter(p -> p.getGrantId() != null)
                            .count()
                            + (int) itsStatusOwnerPermissions.stream()
                            .filter(p -> p.getGrantId() != null)
                            .count()
                            + (int) itsFieldStatusPermissions.stream()
                            .filter(p -> p.getGrantId() != null)
                            .count();

                    Map<Long, Integer> projectGrantsCount = new HashMap<>();

                    for (TransitionRemovalImpactDto.StatusOwnerPermissionImpact perm : itsStatusOwnerPermissions) {
                        if (perm.getProjectGrants() != null) {
                            for (TransitionRemovalImpactDto.ProjectGrantInfo pg : perm.getProjectGrants()) {
                                if (pg.getProjectId() != null) {
                                    projectGrantsCount.merge(pg.getProjectId(), 1, Integer::sum);
                                }
                            }
                        }
                    }

                    for (TransitionRemovalImpactDto.PermissionImpact perm : itsExecutorPermissions) {
                        if (perm.getProjectGrants() != null) {
                            for (TransitionRemovalImpactDto.ProjectGrantInfo pg : perm.getProjectGrants()) {
                                if (pg.getProjectId() != null) {
                                    projectGrantsCount.merge(pg.getProjectId(), 1, Integer::sum);
                                }
                            }
                        }
                    }

                    for (TransitionRemovalImpactDto.FieldStatusPermissionImpact perm : itsFieldStatusPermissions) {
                        if (perm.getProjectGrants() != null) {
                            for (TransitionRemovalImpactDto.ProjectGrantInfo pg : perm.getProjectGrants()) {
                                if (pg.getProjectId() != null) {
                                    projectGrantsCount.merge(pg.getProjectId(), 1, Integer::sum);
                                }
                            }
                        }
                    }

                    int totalProjectGrants = projectGrantsCount.values().stream().mapToInt(Integer::intValue).sum();

                    List<TransitionRemovalImpactDto.ProjectImpact> projectImpacts = projectGrantsCount.entrySet().stream()
                            .map(entry -> {
                                Long projectId = entry.getKey();
                                String projectName;
                                if (its.getProject() != null && its.getProject().getId().equals(projectId)) {
                                    projectName = its.getProject().getName();
                                } else {
                                    projectName = its.getProjectsAssociation().stream()
                                            .filter(project -> project.getId().equals(projectId))
                                            .findFirst()
                                            .map(Project::getName)
                                            .orElse("Progetto " + projectId);
                                }
                                return TransitionRemovalImpactDto.ProjectImpact.builder()
                                        .projectId(projectId)
                                        .projectName(projectName)
                                        .projectGrantsCount(entry.getValue())
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return TransitionRemovalImpactDto.ItemTypeSetImpact.builder()
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



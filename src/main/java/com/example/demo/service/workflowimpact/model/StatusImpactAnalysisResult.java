package com.example.demo.service.workflowimpact.model;

import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Workflow;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Set;

@Value
@Builder
public class StatusImpactAnalysisResult {
    Workflow workflow;
    @Singular("removedStatusId")
    Set<Long> removedStatusIds;
    @Singular("removedStatusName")
    List<String> removedStatusNames;
    @Singular("removedTransitionId")
    Set<Long> removedTransitionIds;
    @Singular("removedTransitionName")
    List<String> removedTransitionNames;
    @Singular("affectedItemTypeSet")
    List<ItemTypeSet> affectedItemTypeSets;
    @Singular("statusOwnerPermissionImpact")
    List<StatusOwnerPermissionImpactData> statusOwnerPermissionImpacts;
    @Singular("executorPermissionImpact")
    List<ExecutorPermissionImpactData> executorPermissionImpacts;
    @Singular("fieldStatusPermissionImpact")
    List<FieldStatusPermissionImpactData> fieldStatusPermissionImpacts;
}








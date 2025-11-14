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
public class TransitionImpactAnalysisResult {
    Workflow workflow;
    @Singular("removedTransitionId")
    Set<Long> removedTransitionIds;
    @Singular("removedTransitionName")
    List<String> removedTransitionNames;
    @Singular("affectedItemTypeSet")
    List<ItemTypeSet> affectedItemTypeSets;
    @Singular("executorPermissionImpact")
    List<ExecutorPermissionImpactData> executorPermissionImpacts;
}









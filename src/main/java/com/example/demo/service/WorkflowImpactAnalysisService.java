package com.example.demo.service;

import com.example.demo.dto.StatusRemovalImpactDto;
import com.example.demo.dto.TransitionRemovalImpactDto;
import com.example.demo.entity.Tenant;
import com.example.demo.service.workflowimpact.StatusImpactAnalyzer;
import com.example.demo.service.workflowimpact.StatusImpactResponseMapper;
import com.example.demo.service.workflowimpact.TransitionImpactAnalyzer;
import com.example.demo.service.workflowimpact.TransitionImpactResponseMapper;
import com.example.demo.service.workflowimpact.model.StatusImpactAnalysisResult;
import com.example.demo.service.workflowimpact.model.TransitionImpactAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkflowImpactAnalysisService {

    private final StatusImpactAnalyzer statusImpactAnalyzer;
    private final TransitionImpactAnalyzer transitionImpactAnalyzer;
    private final StatusImpactResponseMapper statusImpactResponseMapper;
    private final TransitionImpactResponseMapper transitionImpactResponseMapper;

    public StatusRemovalImpactDto analyzeStatusRemovalImpact(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedStatusIds
    ) {
        StatusImpactAnalysisResult analysisResult =
                statusImpactAnalyzer.analyzeStatusRemovalImpact(tenant, workflowId, removedStatusIds);
        return statusImpactResponseMapper.toDto(analysisResult);
    }

    public StatusRemovalImpactDto analyzeStatusRemovalImpact(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedStatusIds,
            Set<Long> actuallyRemovedTransitionIds
    ) {
        StatusImpactAnalysisResult analysisResult =
                statusImpactAnalyzer.analyzeStatusRemovalImpact(tenant, workflowId, removedStatusIds, actuallyRemovedTransitionIds);
        return statusImpactResponseMapper.toDto(analysisResult);
    }

    public TransitionRemovalImpactDto analyzeTransitionRemovalImpact(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedTransitionIds
    ) {
        TransitionImpactAnalysisResult analysisResult =
                transitionImpactAnalyzer.analyzeTransitionRemovalImpact(tenant, workflowId, removedTransitionIds);
        return transitionImpactResponseMapper.toDto(analysisResult);
    }
}


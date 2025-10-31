package com.example.demo.service;

import com.example.demo.dto.WorkflowViewDto;
import com.example.demo.entity.Status;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Workflow;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.metadata.*;
import com.example.demo.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkflowLookup {
    private final ItemTypeConfigurationLookup itemTypeConfigurationLookup;
    private final DtoMapperFacade dtoMapper;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final WorkflowEdgeRepository workflowEdgeRepository;
    private final WorkflowRepository workflowRepository;

    public Workflow getByIdEntity(Tenant tenant, Long workflowId) {
        return workflowRepository.findByIdAndTenant(workflowId, tenant)
                .orElseThrow(() -> new ApiException("Workflow not found"));
    }

    public WorkflowViewDto getById(Tenant tenant, Long workflowId) {
        WorkflowViewDto workflowViewDto = dtoMapper.toWorkflowViewDto(getByIdEntity(tenant, workflowId));
        List<WorkflowNode> workflowNodes = workflowNodeRepository.findByWorkflowIdAndTenant(workflowViewDto.getId(), tenant);
        List<WorkflowEdge> workflowEdges = workflowEdgeRepository.findByWorkflowIdAndTenant(workflowViewDto.getId(), tenant);
        workflowViewDto.setWorkflowNodes(dtoMapper.toWorkflowNodeDtos(workflowNodes));
        workflowViewDto.setWorkflowEdges(dtoMapper.toWorkflowEdgeDtos(workflowEdges));
        return workflowViewDto;
    }

    public List<WorkflowViewDto> getAllByTenant(Tenant tenant) {
        // IMPORTANTE: Mostra solo i workflow con scope TENANT, non quelli di progetto
        return dtoMapper.toWorkflowViewDtos(workflowRepository.findAllByTenantAndScopeTenant(tenant));
    }
    
    public List<WorkflowViewDto> getAllByTenantAndProject(Tenant tenant, Long projectId) {
        return dtoMapper.toWorkflowViewDtos(workflowRepository.findAllByTenantAndProjectId(tenant, projectId));
    }
    
    public WorkflowViewDto getByIdForProject(Tenant tenant, Long workflowId, Long projectId) {
        Workflow workflow = workflowRepository.findByIdAndTenantAndProjectId(workflowId, tenant, projectId)
                .orElseThrow(() -> new ApiException("Workflow not found"));
        WorkflowViewDto workflowViewDto = dtoMapper.toWorkflowViewDto(workflow);
        List<WorkflowNode> workflowNodes = workflowNodeRepository.findByWorkflowIdAndTenant(workflowViewDto.getId(), tenant);
        List<WorkflowEdge> workflowEdges = workflowEdgeRepository.findByWorkflowIdAndTenant(workflowViewDto.getId(), tenant);
        workflowViewDto.setWorkflowNodes(dtoMapper.toWorkflowNodeDtos(workflowNodes));
        workflowViewDto.setWorkflowEdges(dtoMapper.toWorkflowEdgeDtos(workflowEdges));
        return workflowViewDto;
    }

    public boolean isNotInAnyItemTypeSet(Long workflowId, Tenant tenant) {
        return itemTypeConfigurationLookup.isWorkflowNotInAnyItemTypeSet(workflowId, tenant);
    }

    public List<Workflow> findDistinctWorkflowsByStatus(Tenant tenant, Status status) {
        return workflowRepository.findByStatusInTransitions(tenant, status);
    }
}

package com.example.demo.service;
import com.example.demo.dto.TransitionCreateDto;
import com.example.demo.dto.WorkflowCreateDto;
import com.example.demo.dto.WorkflowStatusCreateDto;
import com.example.demo.dto.WorkflowViewDto;
import com.example.demo.entity.Project;
import com.example.demo.entity.Status;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Transition;
import com.example.demo.entity.Workflow;
import com.example.demo.entity.WorkflowStatus;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.metadata.WorkflowEdge;
import com.example.demo.metadata.WorkflowEdgeDto;
import com.example.demo.metadata.WorkflowEdgeRepository;
import com.example.demo.metadata.WorkflowNode;
import com.example.demo.metadata.WorkflowNodeDto;
import com.example.demo.metadata.WorkflowNodeRepository;
import com.example.demo.repository.TransitionRepository;
import com.example.demo.repository.WorkflowRepository;
import com.example.demo.repository.WorkflowStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowCreationService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final TransitionRepository transitionRepository;
    private final WorkflowEdgeRepository workflowEdgeRepository;
    private final StatusLookup statusLookup;
    private final ProjectLookup projectLookup;
    private final DtoMapperFacade dtoMapper;

    @Transactional
    public WorkflowViewDto createGlobal(WorkflowCreateDto dto, Tenant tenant) {
        Workflow workflow = createBaseWorkflow(dto, tenant, ScopeType.TENANT, null);

        Status initialStatus = statusLookup.getById(tenant, dto.initialStatusId());
        workflow.setInitialStatus(initialStatus);

        workflow = workflowRepository.save(workflow);

        Map<Long, WorkflowStatus> statusMap = persistWorkflowStatuses(dto, tenant, workflow);
        persistWorkflowNodes(dto, tenant, workflow, statusMap);

        Map<String, List<Transition>> transitionMap = persistTransitions(dto, tenant, workflow, statusMap);
        persistWorkflowEdges(dto, tenant, workflow, transitionMap);

        return dtoMapper.toWorkflowViewDto(workflow);
    }

    @Transactional
    public WorkflowViewDto createForProject(WorkflowCreateDto dto, Tenant tenant, Long projectId) {
        Project project = projectLookup.getById(tenant, projectId);
        Workflow workflow = createBaseWorkflow(dto, tenant, ScopeType.PROJECT, project);

        Status initialStatus = statusLookup.getById(tenant, dto.initialStatusId());
        workflow.setInitialStatus(initialStatus);

        workflow = workflowRepository.save(workflow);

        Map<Long, WorkflowStatus> statusMap = persistWorkflowStatuses(dto, tenant, workflow);
        persistWorkflowNodes(dto, tenant, workflow, statusMap);

        Map<String, List<Transition>> transitionMap = persistTransitions(dto, tenant, workflow, statusMap);
        persistWorkflowEdges(dto, tenant, workflow, transitionMap);

        return dtoMapper.toWorkflowViewDto(workflow);
    }

    private Map<Long, WorkflowStatus> persistWorkflowStatuses(WorkflowCreateDto dto, Tenant tenant, Workflow workflow) {
        Map<Long, WorkflowStatus> statusMap = new HashMap<>();

        for (WorkflowStatusCreateDto wsDto : dto.workflowStatuses()) {
            Status statusEntity = statusLookup.getById(tenant, wsDto.statusId());
            WorkflowStatus ws = new WorkflowStatus();
            ws.setWorkflow(workflow);
            ws.setStatus(statusEntity);
            ws.setStatusCategory(wsDto.statusCategory());
            ws.setInitial(wsDto.isInitial());
            ws = workflowStatusRepository.save(ws);

            statusMap.put(statusEntity.getId(), ws);
            workflow.getStatuses().add(ws);
        }

        return statusMap;
    }

    private void persistWorkflowNodes(WorkflowCreateDto dto, Tenant tenant, Workflow workflow, Map<Long, WorkflowStatus> statusMap) {
        for (WorkflowNodeDto nodeDto : dto.workflowNodes()) {
            WorkflowNode node = dtoMapper.toWorkflowNodeEntity(nodeDto);
            node.setWorkflow(workflow);
            node.setTenant(tenant);

            WorkflowStatus ws = statusMap.get(nodeDto.statusId());
            if (ws == null) {
                throw new ApiException("StatusId non trovato per node " + nodeDto.statusId());
            }

            node.setWorkflowStatus(ws);
            ws.setNode(node);

            workflowNodeRepository.save(node);
        }
    }

    private Map<String, List<Transition>> persistTransitions(WorkflowCreateDto dto, Tenant tenant, Workflow workflow, Map<Long, WorkflowStatus> statusMap) {
        Map<String, List<Transition>> transitionMap = new HashMap<>();
        List<Transition> savedTransitions = new ArrayList<>();

        for (WorkflowStatusCreateDto wsDto : dto.workflowStatuses()) {
            WorkflowStatus fromWs = statusMap.get(wsDto.statusId());
            if (fromWs == null || wsDto.outgoingTransitions() == null) {
                continue;
            }

            for (TransitionCreateDto tDto : wsDto.outgoingTransitions()) {
                WorkflowStatus toWs = statusMap.get(tDto.toStatusId());
                if (toWs == null) {
                    throw new ApiException("ToStatusId non trovato: " + tDto.toStatusId());
                }

                Transition transition = new Transition();
                transition.setWorkflow(workflow);
                transition.setFromStatus(fromWs);
                transition.setToStatus(toWs);
                transition.setName(tDto.name() != null ? tDto.name() : "");
                transition = transitionRepository.save(transition);

                fromWs.getOutgoingTransitions().add(transition);
                toWs.getIncomingTransitions().add(transition);
                savedTransitions.add(transition);

                String key = fromWs.getStatus().getId() + "-" + toWs.getStatus().getId();
                transitionMap.computeIfAbsent(key, k -> new ArrayList<>()).add(transition);
            }
        }

        workflow.getTransitions().addAll(savedTransitions);
        return transitionMap;
    }

    private void persistWorkflowEdges(WorkflowCreateDto dto, Tenant tenant, Workflow workflow, Map<String, List<Transition>> transitionMap) {
        Map<String, Integer> edgeCounter = new HashMap<>();

        for (WorkflowEdgeDto edgeDto : dto.workflowEdges()) {
            WorkflowEdge edge = dtoMapper.toWorkflowEdgeEntity(edgeDto);
            edge.setWorkflow(workflow);
            edge.setTenant(tenant);

            String key = edgeDto.sourceId() + "-" + edgeDto.targetId();
            List<Transition> transitionsBetween = transitionMap.get(key);
            if (transitionsBetween == null || transitionsBetween.isEmpty()) {
                throw new ApiException("Transition non trovata per edge: " + key);
            }

            int index = edgeCounter.getOrDefault(key, 0);
            if (index >= transitionsBetween.size()) {
                throw new ApiException("Non ci sono abbastanza transizioni per edge: " + key);
            }

            Transition transition = transitionsBetween.get(index);
            edgeCounter.put(key, index + 1);

            edge.setTransition(transition);
            transition.setEdge(edge);

            workflowEdgeRepository.save(edge);
        }
    }

    private Workflow createBaseWorkflow(WorkflowCreateDto dto, Tenant tenant, ScopeType scope, Project project) {
        Workflow workflow = new Workflow();
        workflow.setTenant(tenant);
        workflow.setScope(scope);
        workflow.setProject(project);
        workflow.setName(dto.name());
        return workflow;
    }
}

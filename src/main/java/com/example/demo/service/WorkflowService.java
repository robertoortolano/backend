package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.metadata.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final TransitionRepository transitionRepository;
    private final WorkflowEdgeRepository workflowEdgeRepository;
    private final ItemTypeSetRepository itemTypeSetRepository;
    private final StatusLookup statusLookup;
    private final WorkflowLookup workflowLookup;
    private final DtoMapperFacade dtoMapper;
    private final ItemTypeConfigurationLookup itemTypeConfigurationLookup;

    @Transactional
    public WorkflowViewDto createGlobal(WorkflowCreateDto dto, Tenant tenant) {
        Workflow workflow = createBaseWorkflow(dto, tenant, ScopeType.TENANT);

        // Imposta lo status iniziale
        Status initialStatus = statusLookup.getById(tenant, dto.initialStatusId());
        workflow.setInitialStatus(initialStatus);

        workflow = workflowRepository.save(workflow);

        // ========================
        // WORKFLOW STATUSES
        // ========================
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

        // ========================
        // WORKFLOW NODES
        // ========================
        for (WorkflowNodeDto nodeDto : dto.workflowNodes()) {
            WorkflowNode node = dtoMapper.toWorkflowNodeEntity(nodeDto);
            node.setWorkflow(workflow);
            node.setTenant(tenant);

            WorkflowStatus ws = statusMap.get(nodeDto.statusId());
            if (ws == null) throw new ApiException("StatusId non trovato per node " + nodeDto.statusId());
            node.setWorkflowStatus(ws);
            ws.setNode(node);

            workflowNodeRepository.save(node);
        }

        // ========================
        // TRANSITIONS
        // ========================
        Map<String, List<Transition>> transitionMap = new HashMap<>();
        List<Transition> savedTransitions = new ArrayList<>();

        for (WorkflowStatusCreateDto wsDto : dto.workflowStatuses()) {
            WorkflowStatus fromWs = statusMap.get(wsDto.statusId());
            if (fromWs == null || wsDto.outgoingTransitions() == null) continue;

            for (TransitionCreateDto tDto : wsDto.outgoingTransitions()) {
                WorkflowStatus toWs = statusMap.get(tDto.toStatusId());
                if (toWs == null) throw new ApiException("ToStatusId non trovato: " + tDto.toStatusId());

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

        // ========================
        // EDGES
        // ========================
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

        return dtoMapper.toWorkflowViewDto(workflow);
    }



    @Transactional
    public WorkflowViewDto updateWorkflow(Long workflowId, WorkflowUpdateDto dto, Tenant tenant) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        if (!workflow.getTenant().equals(tenant)) {
            throw new ApiException("Workflow does not belong to tenant");
        }

        // --- Update base workflow ---
        workflow.setName(dto.name());

        // ========================
        // WORKFLOW STATUSES
        // ========================
        Map<Long, WorkflowStatus> existingStatusMap = workflow.getStatuses().stream()
                .collect(Collectors.toMap(ws -> ws.getStatus().getId(), ws -> ws));

        Map<Long, WorkflowStatus> updatedStatusMap = new HashMap<>();

        for (WorkflowStatusUpdateDto wsDto : dto.workflowStatuses()) {
            WorkflowStatus ws = existingStatusMap.remove(wsDto.statusId());
            if (ws == null) {
                ws = new WorkflowStatus();
                ws.setWorkflow(workflow);
                ws.setStatus(statusLookup.getById(tenant, wsDto.statusId()));
            }

            ws.setStatusCategory(wsDto.statusCategory());
            ws.setInitial(wsDto.isInitial());
            workflowStatusRepository.save(ws);
            updatedStatusMap.put(wsDto.statusId(), ws);
        }

        // Cancella status rimasti
        for (WorkflowStatus obsolete : existingStatusMap.values()) {
            workflowStatusRepository.delete(obsolete);
        }
        workflow.getStatuses().clear();
        workflow.getStatuses().addAll(updatedStatusMap.values());

        // ========================
        // WORKFLOW NODES
        // ========================
        Map<Long, WorkflowNode> existingNodes = workflow.getStatuses().stream()
                .map(WorkflowStatus::getNode)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(n -> n.getWorkflowStatus().getStatus().getId(), n -> n));

        for (WorkflowNodeDto nodeDto : dto.workflowNodes()) {
            WorkflowStatus ws = updatedStatusMap.get(nodeDto.statusId());
            if (ws == null) throw new ApiException("WorkflowNode refers to missing statusId: " + nodeDto.statusId());

            WorkflowNode node = existingNodes.remove(nodeDto.statusId());
            if (node == null) {
                node = new WorkflowNode();
                node.setWorkflowStatus(ws);
                node.setWorkflow(workflow);
            }

            node.setTenant(tenant);
            node.setPositionX(nodeDto.positionX());
            node.setPositionY(nodeDto.positionY());
            workflowNodeRepository.save(node);

            ws.setNode(node);
        }

        // Cancella nodi rimasti
        for (WorkflowNode obsolete : existingNodes.values()) {
            workflowNodeRepository.delete(obsolete);
        }

        // ========================
        // TRANSITIONS
        // ========================
        Map<Long, Transition> existingTransitions = workflow.getTransitions().stream()
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(Transition::getId, t -> t));

        Map<Long, Transition> transitionMapById = new HashMap<>();
        Map<String, Transition> transitionMapByTempId = new HashMap<>();

        for (TransitionUpdateDto tDto : dto.transitions()) {
            Transition transition;

            if (tDto.id() != null && existingTransitions.containsKey(tDto.id())) {
                transition = existingTransitions.remove(tDto.id());
            } else {
                transition = new Transition();
            }

            transition.setWorkflow(workflow);
            transition.setFromStatus(updatedStatusMap.get(tDto.fromStatusId()));
            transition.setToStatus(updatedStatusMap.get(tDto.toStatusId()));
            transition.setName(tDto.name() != null ? tDto.name() : "");

            transition = transitionRepository.save(transition);

            if (transition.getId() != null) {
                transitionMapById.put(transition.getId(), transition);
            }
            if (tDto.tempId() != null) {
                transitionMapByTempId.put(tDto.tempId(), transition);
            }
        }

        // Cancella transizioni obsolete
        for (Transition obsolete : existingTransitions.values()) {
            transitionRepository.delete(obsolete);
        }

        workflow.getTransitions().clear();
        workflow.getTransitions().addAll(transitionMapById.values());

        // ========================
        // WORKFLOW EDGES
        // ========================
        Map<Long, WorkflowEdge> existingEdges = workflowEdgeRepository
                .findByWorkflowIdAndTenant(workflow.getId(), tenant)
                .stream()
                .collect(Collectors.toMap(WorkflowEdge::getId, e -> e));

        for (WorkflowEdgeDto edgeDto : dto.workflowEdges()) {
            WorkflowEdge edge = (edgeDto.id() != null && existingEdges.containsKey(edgeDto.id()))
                    ? existingEdges.remove(edgeDto.id())
                    : new WorkflowEdge();

            edge.setWorkflow(workflow);
            edge.setTenant(tenant);
            edge.setSourceId(edgeDto.sourceId());
            edge.setTargetId(edgeDto.targetId());
            edge.setSourcePosition(edgeDto.sourcePosition());
            edge.setTargetPosition(edgeDto.targetPosition());

            Transition transition = null;
            if (edgeDto.transitionId() != null) {
                transition = transitionMapById.get(edgeDto.transitionId());
            } else if (edgeDto.transitionTempId() != null) {
                transition = transitionMapByTempId.get(edgeDto.transitionTempId());
            }

            if (transition != null) {
                edge.setTransition(transition);
                transition.setEdge(edge);
            }

            workflowEdgeRepository.save(edge);
        }

        // Cancella edges obsolete
        for (WorkflowEdge obsolete : existingEdges.values()) {
            workflowEdgeRepository.delete(obsolete);
        }

        // ========================
        // INITIAL STATUS
        // ========================
        if (dto.initialStatusId() != null) {
            WorkflowStatus initialWs = updatedStatusMap.get(dto.initialStatusId());
            if (initialWs == null)
                throw new ApiException("Invalid initialStatusId: " + dto.initialStatusId());
            workflow.setInitialStatus(initialWs.getStatus());
        }

        workflow = workflowRepository.save(workflow);
        return dtoMapper.toWorkflowViewDto(workflow);
    }









    public void delete(Tenant tenant, Long workflowId) {
        Workflow workflow = workflowLookup.getByIdEntity(tenant, workflowId);
        if (!workflowLookup.isNotInAnyItemTypeSet(workflowId, tenant)) {
            throw new ApiException("Workflow is used in an ItemType and cannot be deleted");
        }
        workflowRepository.delete(workflow);
    }

    @Transactional(readOnly = true)
    public WorkflowDetailDto getWorkflowDetail(Long workflowId, Tenant tenant) {
        Workflow workflow = workflowLookup.getByIdEntity(tenant, workflowId);
        List<ItemTypeSet> itemTypeSets = itemTypeSetRepository.findByItemTypeConfigurationsWorkflowIdAndTenant(workflowId, tenant);
        
        return dtoMapper.toWorkflowDetailDto(workflow, itemTypeSets);
    }

    // --- Helpers comuni ---

    private Map<Long, WorkflowStatus> createWorkflowStatuses(Workflow workflow, List<WorkflowStatusCreateDto> statusDtos, Tenant tenant, Status initialStatus) {
        Map<Long, WorkflowStatus> statusMap = new HashMap<>();
        for (WorkflowStatusCreateDto wsDto : statusDtos) {
            Status statusEntity = statusLookup.getById(tenant, wsDto.statusId());
            WorkflowStatus ws = new WorkflowStatus();
            ws.setWorkflow(workflow);
            ws.setStatus(statusEntity);
            ws.setStatusCategory(wsDto.statusCategory());
            ws.setInitial(initialStatus != null && statusEntity.equals(initialStatus));

            ws = workflowStatusRepository.save(ws);
            statusMap.put(statusEntity.getId(), ws);
            workflow.getStatuses().add(ws);
        }
        return statusMap;
    }

    private void createTransitionsAndMetadata(
            Workflow workflow,
            Tenant tenant,
            List<WorkflowStatusCreateDto> statusDtos,
            List<WorkflowNodeDto> nodeDtos,
            List<WorkflowEdgeDto> edgeDtos,
            Map<Long, WorkflowStatus> statusMap
    ) {
        createNodesAndLinkStatuses(workflow, tenant, nodeDtos, statusMap);
        List<Transition> savedTransitions = new ArrayList<>();
        Map<String, List<Transition>> transitionMap = createTransitions(workflow, statusDtos, statusMap, savedTransitions);
        workflow.getTransitions().addAll(savedTransitions);
        createEdgesAndLinkTransitions(workflow, tenant, edgeDtos, transitionMap);
    }

    private void createNodesAndLinkStatuses(
            Workflow workflow,
            Tenant tenant,
            List<WorkflowNodeDto> nodeDtos,
            Map<Long, WorkflowStatus> statusMap
    ) {
        for (WorkflowNodeDto nodeDto : nodeDtos) {
            WorkflowNode node = dtoMapper.toWorkflowNodeEntity(nodeDto);
            //node.setWorkflow(workflow);
            node.setTenant(tenant);

            WorkflowStatus ws = statusMap.get(nodeDto.statusId());
            if (ws == null) throw new ApiException("StatusId non trovato per node " + nodeDto.statusId());
            node.setWorkflowStatus(ws);
            ws.setNode(node);
        }
    }

    private Map<String, List<Transition>> createTransitions(
            Workflow workflow,
            List<WorkflowStatusCreateDto> statusDtos,
            Map<Long, WorkflowStatus> statusMap,
            List<Transition> savedTransitions
    ) {
        Map<String, List<Transition>> transitionMap = new HashMap<>();
        for (WorkflowStatusCreateDto wsDto : statusDtos) {
            WorkflowStatus fromWs = statusMap.get(wsDto.statusId());
            if (fromWs == null) throw new ApiException("WorkflowStatus non trovato per status " + wsDto.statusId());
            if (wsDto.outgoingTransitions() == null) continue;

            for (TransitionCreateDto tDto : wsDto.outgoingTransitions()) {
                WorkflowStatus toWs = statusMap.get(tDto.toStatusId());
                if (toWs == null) throw new ApiException("ToStatusId non trovato: " + tDto.toStatusId());

                Transition transition = new Transition();
                transition.setWorkflow(workflow);
                transition.setFromStatus(fromWs);
                transition.setToStatus(toWs);
                transition.setName(tDto.name() != null ? tDto.name() : "");
                transition = transitionRepository.save(transition);

                String key = fromWs.getStatus().getId() + "-" + toWs.getStatus().getId();
                transitionMap.computeIfAbsent(key, k -> new ArrayList<>()).add(transition);

                fromWs.getOutgoingTransitions().add(transition);
                toWs.getIncomingTransitions().add(transition);
                savedTransitions.add(transition);
            }
        }
        return transitionMap;
    }

    private void createEdgesAndLinkTransitions(
            Workflow workflow,
            Tenant tenant,
            List<WorkflowEdgeDto> edgeDtos,
            Map<String, List<Transition>> transitionMap
    ) {
        Map<String, Integer> edgeCounter = new HashMap<>();
        for (WorkflowEdgeDto edgeDto : edgeDtos) {
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





    private Workflow createBaseWorkflow(WorkflowCreateDto dto, Tenant tenant, ScopeType scope) {
        Workflow workflow = new Workflow();
        workflow.setTenant(tenant);
        workflow.setScope(scope);
        workflow.setName(dto.name());
        return workflow;
    }
}


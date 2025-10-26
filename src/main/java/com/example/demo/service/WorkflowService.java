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
    private final ItemTypeSetLookup itemTypeSetLookup;
    private final StatusOwnerPermissionRepository statusOwnerPermissionRepository;
    private final FieldStatusPermissionRepository fieldStatusPermissionRepository;
    private final ExecutorPermissionRepository executorPermissionRepository;

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
        System.out.println("DEBUG: updateWorkflow called for workflowId=" + workflowId);
        
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        if (!workflow.getTenant().equals(tenant)) {
            throw new ApiException("Workflow does not belong to tenant");
        }

        // Identifica le Transition che verranno rimosse
        Set<Long> existingTransitionIds = workflow.getTransitions().stream()
                .map(Transition::getId)
                .collect(Collectors.toSet());
        
        Set<Long> newTransitionIds = dto.transitions().stream()
                .filter(t -> t.id() != null)
                .map(TransitionUpdateDto::id)
                .collect(Collectors.toSet());
        
        Set<Long> removedTransitionIds = existingTransitionIds.stream()
                .filter(id -> !newTransitionIds.contains(id))
                .collect(Collectors.toSet());

        // Se ci sono Transition rimosse, analizza gli impatti
        if (!removedTransitionIds.isEmpty()) {
            TransitionRemovalImpactDto impact = analyzeTransitionRemovalImpact(tenant, workflowId, removedTransitionIds);
            
            // Se ci sono ExecutorPermissions con assegnazioni, restituisci il report per conferma
            if (impact.getExecutorPermissions().stream().anyMatch(TransitionRemovalImpactDto.PermissionImpact::isHasAssignments)) {
                // Lancia un'eccezione speciale per indicare che serve conferma
                throw new ApiException("TRANSITION_REMOVAL_IMPACT: Ci sono ExecutorPermissions con ruoli assegnati che verranno rimosse");
            }
        }

        // Identifica gli Status che verranno rimossi
        Set<Long> existingStatusIds = workflow.getStatuses().stream()
                .map(WorkflowStatus::getId)
                .collect(Collectors.toSet());
        
        Set<Long> newStatusIds = dto.workflowStatuses().stream()
                .filter(ws -> ws.id() != null)
                .map(WorkflowStatusUpdateDto::id)
                .collect(Collectors.toSet());
        
        Set<Long> removedStatusIds = existingStatusIds.stream()
                .filter(id -> !newStatusIds.contains(id))
                .collect(Collectors.toSet());

        // Se ci sono Status rimossi, analizza gli impatti
        if (!removedStatusIds.isEmpty()) {
            StatusRemovalImpactDto impact = analyzeStatusRemovalImpact(tenant, workflowId, removedStatusIds);
            
            // Se ci sono StatusOwnerPermissions con assegnazioni, restituisci il report per conferma
            if (impact.getStatusOwnerPermissions().stream().anyMatch(StatusRemovalImpactDto.PermissionImpact::isHasAssignments)) {
                // Lancia un'eccezione speciale per indicare che serve conferma
                throw new ApiException("STATUS_REMOVAL_IMPACT: Ci sono StatusOwnerPermissions con ruoli assegnati che verranno rimosse");
            }
        }

        // Procedi con l'aggiornamento normale del workflow
        return performWorkflowUpdate(workflow, dto, tenant);
    }

    private WorkflowViewDto performWorkflowUpdate(Workflow workflow, WorkflowUpdateDto dto, Tenant tenant) {
        // --- Update base workflow ---
        workflow.setName(dto.name());

        // ========================
        // WORKFLOW STATUSES
        // ========================
        // Mappa per Status ID -> WorkflowStatus (per il processing)
        Map<Long, WorkflowStatus> existingStatusMap = workflow.getStatuses().stream()
                .collect(Collectors.toMap(ws -> ws.getStatus().getId(), ws -> ws));

        // Mappa per WorkflowStatus ID -> WorkflowStatus (per identificare i nuovi)
        Map<Long, WorkflowStatus> existingWorkflowStatusMap = workflow.getStatuses().stream()
                .collect(Collectors.toMap(ws -> ws.getId(), ws -> ws));

        // Cattura gli ID dei WorkflowStatus esistenti per identificare i nuovi
        Set<Long> existingWorkflowStatusIds = existingWorkflowStatusMap.keySet();

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

        // Gestisce le permissions per i nuovi WorkflowStatus aggiunti (DOPO l'aggiornamento)
        System.out.println("DEBUG: About to call handlePermissionsForNewWorkflowStatuses");
        handlePermissionsForNewWorkflowStatuses(tenant, workflow, existingWorkflowStatusIds);

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
            // Prima rimuovi le ExecutorPermissions associate direttamente dal database
            List<ExecutorPermission> permissions = executorPermissionRepository
                    .findByTransitionId(obsolete.getId());
            for (ExecutorPermission permission : permissions) {
                executorPermissionRepository.delete(permission);
            }
            // Poi elimina la Transition
            transitionRepository.delete(obsolete);
        }

        workflow.getTransitions().clear();
        workflow.getTransitions().addAll(transitionMapById.values());

        // Gestisce le permissions per le nuove Transition aggiunte
        handlePermissionsForNewTransitions(tenant, workflow, existingTransitions.keySet());

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

    /**
     * Gestisce le permissions per i nuovi WorkflowStatus aggiunti al Workflow
     */
    private void handlePermissionsForNewWorkflowStatuses(
            Tenant tenant, 
            Workflow workflow, 
            Set<Long> existingWorkflowStatusIds
    ) {
        // Debug: log degli status esistenti
        System.out.println("DEBUG: existingWorkflowStatusIds = " + existingWorkflowStatusIds);
        
        // Trova i nuovi WorkflowStatus aggiunti
        Set<Long> newWorkflowStatusIds = workflow.getStatuses().stream()
                .map(ws -> ws.getId())
                .filter(id -> !existingWorkflowStatusIds.contains(id))
                .collect(Collectors.toSet());
        
        // Debug: log dei nuovi status
        System.out.println("DEBUG: newWorkflowStatusIds = " + newWorkflowStatusIds);
        
        if (newWorkflowStatusIds.isEmpty()) {
            return; // Nessun nuovo WorkflowStatus aggiunto
        }
        
        // Se tutti gli status sono "nuovi", potrebbe essere un workflow nuovo
        // In questo caso creiamo le permissions per tutti gli status
        if (newWorkflowStatusIds.size() == workflow.getStatuses().size()) {
            System.out.println("DEBUG: All status are 'new', creating permissions for all status (new workflow)");
        }
        
        // Trova tutti gli ItemTypeSet che usano questo Workflow
        List<ItemTypeSet> affectedItemTypeSets = findItemTypeSetsUsingWorkflow(workflow.getId(), tenant);
        
        // Per ogni ItemTypeSet, crea le permissions per i nuovi WorkflowStatus
        for (ItemTypeSet itemTypeSet : affectedItemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                if (config.getWorkflow().getId().equals(workflow.getId())) {
                    // Crea le permissions per i nuovi WorkflowStatus
                    createPermissionsForNewWorkflowStatuses(config, newWorkflowStatusIds);
                }
            }
        }
    }
    
    /**
     * Trova tutti gli ItemTypeSet che usano un Workflow specifico
     */
    private List<ItemTypeSet> findItemTypeSetsUsingWorkflow(Long workflowId, Tenant tenant) {
        return itemTypeSetLookup.findByWorkflowId(workflowId, tenant);
    }
    
    /**
     * Crea le permissions per i nuovi WorkflowStatus in un ItemTypeConfiguration
     */
    private void createPermissionsForNewWorkflowStatuses(
            ItemTypeConfiguration config, 
            Set<Long> newWorkflowStatusIds
    ) {
        for (Long workflowStatusId : newWorkflowStatusIds) {
            // Crea STATUS_OWNERS permission per il nuovo WorkflowStatus
            createStatusOwnerPermission(config, workflowStatusId);
            
            // Crea EDITORS/VIEWERS permissions per le coppie (FieldConfiguration, WorkflowStatus)
            createFieldStatusPermissionsForNewStatus(config, workflowStatusId);
        }
    }
    
    /**
     * Crea STATUS_OWNERS permission per un WorkflowStatus
     */
    private void createStatusOwnerPermission(ItemTypeConfiguration config, Long workflowStatusId) {
        // Verifica se la permission esiste già
        StatusOwnerPermission existingPermission = statusOwnerPermissionRepository
                .findByItemTypeConfigurationAndWorkflowStatusId(config, workflowStatusId);
        
        if (existingPermission == null) {
            // Crea nuova permission
            WorkflowStatus workflowStatus = workflowStatusRepository.findById(workflowStatusId)
                    .orElseThrow(() -> new ApiException("WorkflowStatus not found: " + workflowStatusId));
            
            StatusOwnerPermission permission = new StatusOwnerPermission();
            permission.setItemTypeConfiguration(config);
            permission.setWorkflowStatus(workflowStatus);
            permission.setAssignedRoles(new HashSet<>());
            
            statusOwnerPermissionRepository.save(permission);
        }
    }
    
    /**
     * Crea EDITORS/VIEWERS permissions per le coppie (FieldConfiguration, WorkflowStatus)
     */
    private void createFieldStatusPermissionsForNewStatus(ItemTypeConfiguration config, Long workflowStatusId) {
        WorkflowStatus workflowStatus = workflowStatusRepository.findById(workflowStatusId)
                .orElseThrow(() -> new ApiException("WorkflowStatus not found: " + workflowStatusId));
        
        // Trova tutte le FieldConfiguration del FieldSet associato
        List<FieldConfiguration> fieldConfigurations = config.getFieldSet().getFieldSetEntries().stream()
                .map(FieldSetEntry::getFieldConfiguration)
                .toList();
        
        for (FieldConfiguration fieldConfig : fieldConfigurations) {
            // Crea EDITORS permission
            createFieldStatusPermission(config, fieldConfig, workflowStatus, FieldStatusPermission.PermissionType.EDITORS);
            
            // Crea VIEWERS permission
            createFieldStatusPermission(config, fieldConfig, workflowStatus, FieldStatusPermission.PermissionType.VIEWERS);
        }
    }
    
    /**
     * Crea una FieldStatusPermission specifica
     */
    private void createFieldStatusPermission(
            ItemTypeConfiguration config,
            FieldConfiguration fieldConfig,
            WorkflowStatus workflowStatus,
            FieldStatusPermission.PermissionType permissionType
    ) {
        // Verifica se la permission esiste già
        FieldStatusPermission existingPermission = fieldStatusPermissionRepository
                .findByItemTypeConfigurationAndFieldConfigurationAndWorkflowStatusAndPermissionType(
                        config, fieldConfig, workflowStatus, permissionType);
        
        if (existingPermission == null) {
            // Crea nuova permission
            FieldStatusPermission permission = new FieldStatusPermission();
            permission.setItemTypeConfiguration(config);
            permission.setFieldConfiguration(fieldConfig);
            permission.setWorkflowStatus(workflowStatus);
            permission.setPermissionType(permissionType);
            permission.setAssignedRoles(new HashSet<>());
            
            fieldStatusPermissionRepository.save(permission);
        }
    }

    /**
     * Gestisce le permissions per le nuove Transition aggiunte al Workflow
     */
    private void handlePermissionsForNewTransitions(
            Tenant tenant, 
            Workflow workflow, 
            Set<Long> existingTransitionIds
    ) {
        // Identifica le Transition esistenti per coppia (fromStatusId, toStatusId)
        Set<String> existingTransitionKeys = existingTransitionIds.stream()
                .map(id -> {
                    Transition t = transitionRepository.findById(id).orElse(null);
                    if (t != null && t.getFromStatus() != null && t.getToStatus() != null) {
                        return t.getFromStatus().getStatus().getId() + "->" + t.getToStatus().getStatus().getId();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        // Trova le nuove Transition aggiunte (per coppia di status)
        Set<Long> newTransitionIds = workflow.getTransitions().stream()
                .filter(t -> t.getId() != null)
                .filter(t -> {
                    if (t.getFromStatus() != null && t.getToStatus() != null) {
                        String key = t.getFromStatus().getStatus().getId() + "->" + t.getToStatus().getStatus().getId();
                        return !existingTransitionKeys.contains(key);
                    }
                    return false;
                })
                .map(Transition::getId)
                .collect(Collectors.toSet());
        
        if (newTransitionIds.isEmpty()) {
            return; // Nessuna nuova Transition aggiunta
        }
        
        // Trova tutti gli ItemTypeSet che usano questo Workflow
        List<ItemTypeSet> affectedItemTypeSets = findItemTypeSetsUsingWorkflow(workflow.getId(), tenant);
        
        // Per ogni ItemTypeSet, crea le permissions per le nuove Transition
        for (ItemTypeSet itemTypeSet : affectedItemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                if (config.getWorkflow().getId().equals(workflow.getId())) {
                    // Crea le permissions per le nuove Transition
                    createPermissionsForNewTransitions(config, newTransitionIds);
                }
            }
        }
    }
    
    /**
     * Crea le permissions per le nuove Transition in un ItemTypeConfiguration
     */
    private void createPermissionsForNewTransitions(
            ItemTypeConfiguration config, 
            Set<Long> newTransitionIds
    ) {
        for (Long transitionId : newTransitionIds) {
            // Crea EXECUTORS permission per la nuova Transition
            createExecutorPermission(config, transitionId);
        }
    }
    
    /**
     * Crea EXECUTORS permission per una Transition
     */
    private void createExecutorPermission(ItemTypeConfiguration config, Long transitionId) {
        // Verifica se la permission esiste già
        ExecutorPermission existingPermission = executorPermissionRepository
                .findByItemTypeConfigurationAndTransitionId(config, transitionId);
        
        if (existingPermission == null) {
            // Crea nuova permission
            Transition transition = transitionRepository.findById(transitionId)
                    .orElseThrow(() -> new ApiException("Transition not found: " + transitionId));
            
            ExecutorPermission permission = new ExecutorPermission();
            permission.setItemTypeConfiguration(config);
            permission.setTransition(transition);
            permission.setAssignedRoles(new HashSet<>());
            
            executorPermissionRepository.save(permission);
        }
    }
    
    // ========================
    // TRANSITION REMOVAL IMPACT MANAGEMENT
    // ========================
    
    /**
     * Analizza gli impatti della rimozione di Transition da un Workflow
     */
    @Transactional(readOnly = true)
    public TransitionRemovalImpactDto analyzeTransitionRemovalImpact(
            Tenant tenant, 
            Long workflowId, 
            Set<Long> removedTransitionIds
    ) {
        Workflow workflow = workflowLookup.getByIdEntity(tenant, workflowId);
        
        // Trova tutti gli ItemTypeSet che usano questo Workflow
        List<ItemTypeSet> allItemTypeSetsUsingWorkflow = findItemTypeSetsUsingWorkflow(workflowId, tenant);
        
        // Analizza le ExecutorPermissions che verranno rimosse
        List<TransitionRemovalImpactDto.PermissionImpact> executorPermissions = 
                analyzeExecutorPermissionImpacts(allItemTypeSetsUsingWorkflow, removedTransitionIds);
        
        // Calcola solo gli ItemTypeSet che hanno effettivamente impatti (permissions con ruoli assegnati)
        Set<Long> itemTypeSetIdsWithImpact = executorPermissions.stream()
                .map(TransitionRemovalImpactDto.PermissionImpact::getItemTypeSetId)
                .collect(Collectors.toSet());
        
        List<ItemTypeSet> affectedItemTypeSets = allItemTypeSetsUsingWorkflow.stream()
                .filter(its -> itemTypeSetIdsWithImpact.contains(its.getId()))
                .collect(Collectors.toList());
        
        // Calcola le statistiche
        int totalExecutorPermissions = executorPermissions.size();
        int totalRoleAssignments = executorPermissions.stream()
                .mapToInt(perm -> perm.getAssignedRoles().size())
                .sum();
        
        // Ottieni i nomi delle Transition rimosse
        List<String> removedTransitionNames = getTransitionNames(removedTransitionIds, tenant);
        
        return TransitionRemovalImpactDto.builder()
                .workflowId(workflowId)
                .workflowName(workflow.getName())
                .removedTransitionIds(new ArrayList<>(removedTransitionIds))
                .removedTransitionNames(removedTransitionNames)
                .affectedItemTypeSets(mapItemTypeSetImpacts(affectedItemTypeSets))
                .executorPermissions(executorPermissions)
                .totalAffectedItemTypeSets(affectedItemTypeSets.size()) // Solo quelli con impatti effettivi
                .totalExecutorPermissions(totalExecutorPermissions)
                .totalRoleAssignments(totalRoleAssignments)
                .build();
    }
    
    /**
     * Rimuove le ExecutorPermissions orfane per le Transition rimosse
     */
    public void removeOrphanedExecutorPermissions(
            Tenant tenant, 
            Long workflowId, 
            Set<Long> removedTransitionIds
    ) {
        // Trova tutti gli ItemTypeSet che usano questo Workflow
        List<ItemTypeSet> affectedItemTypeSets = findItemTypeSetsUsingWorkflow(workflowId, tenant);
        
        // Rimuovi ExecutorPermissions orfane
        removeOrphanedExecutorPermissions(affectedItemTypeSets, removedTransitionIds);
    }
    
    /**
     * Analizza le ExecutorPermissions che verranno rimosse
     */
    private List<TransitionRemovalImpactDto.PermissionImpact> analyzeExecutorPermissionImpacts(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedTransitionIds
    ) {
        List<TransitionRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                // Trova le ExecutorPermissions per le Transition rimosse
                List<ExecutorPermission> permissions = executorPermissionRepository
                        .findByItemTypeConfigurationAndTransitionIdIn(config, removedTransitionIds);
                
                for (ExecutorPermission permission : permissions) {
                    Transition transition = permission.getTransition();
                    
                    // Raccogli ruoli assegnati
                    List<String> assignedRoles = permission.getAssignedRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList());
                    
                    boolean hasAssignments = !assignedRoles.isEmpty();
                    
                    // Solo se ha ruoli assegnati
                    if (hasAssignments) {
                        impacts.add(TransitionRemovalImpactDto.PermissionImpact.builder()
                                .permissionId(permission.getId())
                                .permissionType("EXECUTORS")
                                .itemTypeSetId(itemTypeSet.getId())
                                .itemTypeSetName(itemTypeSet.getName())
                                .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                                .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                                .transitionId(transition.getId())
                                .transitionName(formatTransitionName(transition))
                                .fromStatusName(transition.getFromStatus().getStatus().getName())
                                .toStatusName(transition.getToStatus().getStatus().getName())
                                .assignedRoles(assignedRoles)
                                .hasAssignments(true)
                                .build());
                    }
                }
            }
        }
        
        return impacts;
    }
    
    /**
     * Rimuove le ExecutorPermissions orfane per le Transition rimosse
     */
    private void removeOrphanedExecutorPermissions(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedTransitionIds
    ) {
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                // Trova e rimuovi le ExecutorPermissions per le Transition rimosse
                List<ExecutorPermission> permissions = executorPermissionRepository
                        .findByItemTypeConfigurationAndTransitionIdIn(config, removedTransitionIds);
                
                for (ExecutorPermission permission : permissions) {
                    executorPermissionRepository.delete(permission);
                }
            }
        }
    }
    
    /**
     * Rimuove le ExecutorPermissions per una singola Transition
     */
    public void removeExecutorPermissionsForTransition(Tenant tenant, Long transitionId) {
        // Trova tutte le ExecutorPermissions per questa Transition
        List<ExecutorPermission> permissions = executorPermissionRepository
                .findByTransitionId(transitionId);
        
        // Rimuovi tutte le permission
        for (ExecutorPermission permission : permissions) {
            executorPermissionRepository.delete(permission);
        }
    }
    
    /**
     * Rimuove una Transition in modo sicuro
     */
    public void removeTransition(Tenant tenant, Long transitionId) {
        Transition transition = transitionRepository.findById(transitionId)
                .orElseThrow(() -> new ApiException("Transition not found"));

        if (transition.getWorkflow().isDefaultWorkflow()) {
            throw new ApiException("Default Workflow transition cannot be deleted");
        }

        if (!transition.getWorkflow().getTenant().equals(tenant)) {
            throw new ApiException("Illegal Tenant");
        }

        // Prima rimuovi le ExecutorPermissions associate
        removeExecutorPermissionsForTransition(tenant, transitionId);
        
        // Poi elimina la Transition
        transitionRepository.deleteById(transitionId);
    }
    
    /**
     * Conferma la rimozione delle Transition dopo l'analisi degli impatti
     */
    @Transactional
    public WorkflowViewDto confirmTransitionRemoval(Long workflowId, WorkflowUpdateDto dto, Tenant tenant) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        if (!workflow.getTenant().equals(tenant)) {
            throw new ApiException("Workflow does not belong to tenant");
        }

        // Identifica le Transition che verranno rimosse
        Set<Long> existingTransitionIds = workflow.getTransitions().stream()
                .map(Transition::getId)
                .collect(Collectors.toSet());
        
        Set<Long> newTransitionIds = dto.transitions().stream()
                .filter(t -> t.id() != null)
                .map(TransitionUpdateDto::id)
                .collect(Collectors.toSet());
        
        Set<Long> removedTransitionIds = existingTransitionIds.stream()
                .filter(id -> !newTransitionIds.contains(id))
                .collect(Collectors.toSet());

        // Rimuovi le ExecutorPermissions orfane per le Transition rimosse
        if (!removedTransitionIds.isEmpty()) {
            removeOrphanedExecutorPermissions(tenant, workflowId, removedTransitionIds);
        }

        // Procedi con l'aggiornamento normale del workflow
        return performWorkflowUpdate(workflow, dto, tenant);
    }
    
    /**
     * Ottiene i nomi delle Transition rimosse nel formato "Nome (Da Stato -> A Stato)" o "Da Stato -> A Stato"
     */
    private List<String> getTransitionNames(Set<Long> transitionIds, Tenant tenant) {
        return transitionIds.stream()
                .map(id -> {
                    try {
                        Transition transition = transitionRepository.findById(id).orElse(null);
                        if (transition == null) {
                            return "Transition " + id;
                        }
                        return formatTransitionName(transition);
                    } catch (Exception e) {
                        return "Transition " + id;
                    }
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Formatta il nome di una Transition nel formato "Nome (Da Stato -> A Stato)" o "Da Stato -> A Stato"
     */
    private String formatTransitionName(Transition transition) {
        String fromStatusName = transition.getFromStatus().getStatus().getName();
        String toStatusName = transition.getToStatus().getStatus().getName();
        String transitionName = transition.getName();
        
        // Se la transizione ha un nome, usa il formato "Nome (Da Stato -> A Stato)"
        if (transitionName != null && !transitionName.trim().isEmpty()) {
            return String.format("%s (%s -> %s)", transitionName.trim(), fromStatusName, toStatusName);
        } else {
            // Se non ha nome, usa solo "Da Stato -> A Stato"
            return String.format("%s -> %s", fromStatusName, toStatusName);
        }
    }
    
    /**
     * Analizza gli impatti della rimozione di Status da un Workflow
     */
    @Transactional(readOnly = true)
    public StatusRemovalImpactDto analyzeStatusRemovalImpact(
            Tenant tenant,
            Long workflowId,
            Set<Long> removedStatusIds
    ) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        if (!workflow.getTenant().equals(tenant)) {
            throw new ApiException("Workflow does not belong to tenant");
        }

        // Trova tutti gli ItemTypeSet che usano questo Workflow
        List<ItemTypeSet> allItemTypeSetsUsingWorkflow = findItemTypeSetsUsingWorkflow(workflowId, tenant);
        
        // Analizza le StatusOwnerPermissions che verranno rimosse
        List<StatusRemovalImpactDto.PermissionImpact> statusOwnerPermissions = 
                analyzeStatusOwnerPermissionImpacts(allItemTypeSetsUsingWorkflow, removedStatusIds);
        
        // Calcola solo gli ItemTypeSet che hanno effettivamente impatti (permissions con ruoli assegnati)
        Set<Long> itemTypeSetIdsWithImpact = statusOwnerPermissions.stream()
                .map(StatusRemovalImpactDto.PermissionImpact::getItemTypeSetId)
                .collect(Collectors.toSet());
        
        List<ItemTypeSet> affectedItemTypeSets = allItemTypeSetsUsingWorkflow.stream()
                .filter(its -> itemTypeSetIdsWithImpact.contains(its.getId()))
                .collect(Collectors.toList());
        
        // Calcola le statistiche
        int totalStatusOwnerPermissions = statusOwnerPermissions.size();
        int totalRoleAssignments = statusOwnerPermissions.stream()
                .mapToInt(perm -> perm.getAssignedRoles().size())
                .sum();
        
        // Ottieni i nomi degli Status rimossi
        List<String> removedStatusNames = getStatusNames(removedStatusIds, tenant);
        
        return StatusRemovalImpactDto.builder()
                .workflowId(workflowId)
                .workflowName(workflow.getName())
                .removedStatusIds(new ArrayList<>(removedStatusIds))
                .removedStatusNames(removedStatusNames)
                .affectedItemTypeSets(mapItemTypeSetImpactsForStatus(affectedItemTypeSets))
                .statusOwnerPermissions(statusOwnerPermissions)
                .totalAffectedItemTypeSets(affectedItemTypeSets.size()) // Solo quelli con impatti effettivi
                .totalStatusOwnerPermissions(totalStatusOwnerPermissions)
                .totalRoleAssignments(totalRoleAssignments)
                .build();
    }
    
    /**
     * Rimuove le StatusOwnerPermissions orfane per gli Status rimossi
     */
    public void removeOrphanedStatusOwnerPermissions(
            Tenant tenant, 
            Long workflowId,
            Set<Long> removedStatusIds
    ) {
        // Trova tutti gli ItemTypeSet che usano questo Workflow
        List<ItemTypeSet> affectedItemTypeSets = findItemTypeSetsUsingWorkflow(workflowId, tenant);
        
        for (ItemTypeSet itemTypeSet : affectedItemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                // Trova le StatusOwnerPermissions per gli Status rimossi
                List<StatusOwnerPermission> permissions = statusOwnerPermissionRepository
                        .findByItemTypeConfigurationAndWorkflowStatusIdIn(config, removedStatusIds);
                
                // Rimuovi tutte le permission
                for (StatusOwnerPermission permission : permissions) {
                    statusOwnerPermissionRepository.delete(permission);
                }
            }
        }
    }
    
    /**
     * Rimuove le StatusOwnerPermissions per un singolo Status
     */
    public void removeStatusOwnerPermissionsForStatus(Tenant tenant, Long workflowStatusId) {
        // Trova tutte le StatusOwnerPermissions per questo WorkflowStatus
        List<StatusOwnerPermission> permissions = statusOwnerPermissionRepository
                .findByWorkflowStatusId(workflowStatusId);
        
        // Rimuovi tutte le permission
        for (StatusOwnerPermission permission : permissions) {
            statusOwnerPermissionRepository.delete(permission);
        }
    }
    
    /**
     * Rimuove uno Status in modo sicuro
     */
    public void removeStatus(Tenant tenant, Long workflowStatusId) {
        WorkflowStatus workflowStatus = workflowStatusRepository.findById(workflowStatusId)
                .orElseThrow(() -> new ApiException("WorkflowStatus not found"));

        if (workflowStatus.getWorkflow().isDefaultWorkflow()) {
            throw new ApiException("Default Workflow status cannot be deleted");
        }

        if (!workflowStatus.getWorkflow().getTenant().equals(tenant)) {
            throw new ApiException("Illegal Tenant");
        }

        // Prima rimuovi le StatusOwnerPermissions associate
        removeStatusOwnerPermissionsForStatus(tenant, workflowStatusId);
        
        // Poi elimina il WorkflowStatus
        workflowStatusRepository.deleteById(workflowStatusId);
    }
    
    /**
     * Conferma la rimozione degli Status dopo l'analisi degli impatti
     */
    @Transactional
    public WorkflowViewDto confirmStatusRemoval(Long workflowId, WorkflowUpdateDto dto, Tenant tenant) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        if (!workflow.getTenant().equals(tenant)) {
            throw new ApiException("Workflow does not belong to tenant");
        }

        // Identifica gli Status che verranno rimossi
        Set<Long> existingStatusIds = workflow.getStatuses().stream()
                .map(WorkflowStatus::getId)
                .collect(Collectors.toSet());
        
        Set<Long> newStatusIds = dto.workflowStatuses().stream()
                .filter(ws -> ws.id() != null)
                .map(WorkflowStatusUpdateDto::id)
                .collect(Collectors.toSet());
        
        Set<Long> removedStatusIds = existingStatusIds.stream()
                .filter(id -> !newStatusIds.contains(id))
                .collect(Collectors.toSet());

        // Rimuovi le StatusOwnerPermissions orfane per gli Status rimossi
        if (!removedStatusIds.isEmpty()) {
            removeOrphanedStatusOwnerPermissions(tenant, workflowId, removedStatusIds);
            
            // Rimuovi fisicamente gli Status dal database
            for (Long statusId : removedStatusIds) {
                WorkflowStatus workflowStatus = workflowStatusRepository.findById(statusId).orElse(null);
                if (workflowStatus != null) {
                    // Rimuovi prima le relazioni
                    workflowStatus.getOutgoingTransitions().clear();
                    workflowStatus.getIncomingTransitions().clear();
                    workflowStatus.getOwners().clear();
                    
                    // Rimuovi il WorkflowStatus
                    workflowStatusRepository.delete(workflowStatus);
                }
            }
            
            // Rimuovi anche le Transition obsolete che partono o arrivano agli Status rimossi
            List<Transition> obsoleteTransitions = transitionRepository.findByWorkflow(workflow)
                    .stream()
                    .filter(t -> removedStatusIds.contains(t.getFromStatus().getId()) || 
                                removedStatusIds.contains(t.getToStatus().getId()))
                    .collect(Collectors.toList());
            
            for (Transition obsoleteTransition : obsoleteTransitions) {
                // Rimuovi le ExecutorPermissions associate direttamente dal database
                List<ExecutorPermission> permissions = executorPermissionRepository
                        .findByTransitionId(obsoleteTransition.getId());
                for (ExecutorPermission permission : permissions) {
                    executorPermissionRepository.delete(permission);
                }
                // Rimuovi la Transition
                transitionRepository.delete(obsoleteTransition);
            }
        }

        // Procedi con l'aggiornamento normale del workflow
        return performWorkflowUpdate(workflow, dto, tenant);
    }
    
    /**
     * Analizza le StatusOwnerPermissions che verranno rimosse
     */
    private List<StatusRemovalImpactDto.PermissionImpact> analyzeStatusOwnerPermissionImpacts(
            List<ItemTypeSet> itemTypeSets, 
            Set<Long> removedStatusIds
    ) {
        List<StatusRemovalImpactDto.PermissionImpact> impacts = new ArrayList<>();
        
        for (ItemTypeSet itemTypeSet : itemTypeSets) {
            for (ItemTypeConfiguration config : itemTypeSet.getItemTypeConfigurations()) {
                // Trova le StatusOwnerPermissions per gli Status rimossi
                List<StatusOwnerPermission> permissions = statusOwnerPermissionRepository
                        .findByItemTypeConfigurationAndWorkflowStatusIdIn(config, removedStatusIds);
                
                for (StatusOwnerPermission permission : permissions) {
                    WorkflowStatus workflowStatus = permission.getWorkflowStatus();
                    
                    // Raccogli ruoli assegnati
                    List<String> assignedRoles = permission.getAssignedRoles().stream()
                            .map(Role::getName)
                            .collect(Collectors.toList());
                    
                    boolean hasAssignments = !assignedRoles.isEmpty();
                    
                    // Solo se ha ruoli assegnati
                    if (hasAssignments) {
                        impacts.add(StatusRemovalImpactDto.PermissionImpact.builder()
                                .permissionId(permission.getId())
                                .permissionType("STATUS_OWNERS")
                                .itemTypeSetId(itemTypeSet.getId())
                                .itemTypeSetName(itemTypeSet.getName())
                                .projectId(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getId() : null)
                                .projectName(itemTypeSet.getProject() != null ? itemTypeSet.getProject().getName() : null)
                                .workflowStatusId(workflowStatus.getId())
                                .workflowStatusName(workflowStatus.getStatus().getName())
                                .statusName(workflowStatus.getStatus().getName())
                                .statusCategory(workflowStatus.getStatusCategory().toString())
                                .assignedRoles(assignedRoles)
                                .hasAssignments(true)
                                .build());
                    }
                }
            }
        }
        
        return impacts;
    }
    
    /**
     * Ottiene i nomi degli Status rimossi
     */
    private List<String> getStatusNames(Set<Long> statusIds, Tenant tenant) {
        return statusIds.stream()
                .map(id -> {
                    try {
                        WorkflowStatus workflowStatus = workflowStatusRepository.findById(id).orElse(null);
                        if (workflowStatus == null) {
                            return "Status " + id;
                        }
                        return workflowStatus.getStatus().getName();
                    } catch (Exception e) {
                        return "Status " + id;
                    }
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Ottiene gli ID degli Status di un Workflow
     */
    public Set<Long> getWorkflowStatusIds(Long workflowId, Tenant tenant) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ApiException("Workflow not found: " + workflowId));

        if (!workflow.getTenant().equals(tenant)) {
            throw new ApiException("Workflow does not belong to tenant");
        }

        return workflow.getStatuses().stream()
                .map(WorkflowStatus::getId)
                .collect(Collectors.toSet());
    }
    
    /**
     * Mappa gli ItemTypeSet in impatti per Transition
     */
    private List<TransitionRemovalImpactDto.ItemTypeSetImpact> mapItemTypeSetImpacts(List<ItemTypeSet> itemTypeSets) {
        return itemTypeSets.stream()
                .map(its -> TransitionRemovalImpactDto.ItemTypeSetImpact.builder()
                        .itemTypeSetId(its.getId())
                        .itemTypeSetName(its.getName())
                        .projectId(its.getProject() != null ? its.getProject().getId() : null)
                        .projectName(its.getProject() != null ? its.getProject().getName() : null)
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * Mappa gli ItemTypeSet in impatti per Status
     */
    private List<StatusRemovalImpactDto.ItemTypeSetImpact> mapItemTypeSetImpactsForStatus(List<ItemTypeSet> itemTypeSets) {
        return itemTypeSets.stream()
                .map(its -> StatusRemovalImpactDto.ItemTypeSetImpact.builder()
                        .itemTypeSetId(its.getId())
                        .itemTypeSetName(its.getName())
                        .projectId(its.getProject() != null ? its.getProject().getId() : null)
                        .projectName(its.getProject() != null ? its.getProject().getName() : null)
                        .build())
                .collect(Collectors.toList());
    }
}


package com.example.demo.initializer;

import com.example.demo.config.DefaultConfig;
import com.example.demo.config.DefaultConfigLoader;
import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.enums.StatusCategory;
import com.example.demo.exception.ApiException;
import com.example.demo.metadata.WorkflowEdge;
import com.example.demo.metadata.WorkflowEdgeRepository;
import com.example.demo.metadata.WorkflowNode;
import com.example.demo.metadata.WorkflowNodeRepository;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@Order(5)
@RequiredArgsConstructor
public class WorkflowInitializer implements TenantInitializer {

    private final WorkflowRepository workflowRepository;
    private final WorkflowNodeRepository workflowNodeRepository;
    private final WorkflowEdgeRepository workflowEdgeRepository;
    private final TransitionRepository transitionRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final StatusRepository statusRepository;

    static final String STATUS_NOT_FOUND = "Status non trovato: ";
    static final String WORKFLOW_STATUS_NOT_FOUND = "WorkflowStatus non trovato: ";

    @Override

    public void initialize(Tenant tenant) {
        DefaultConfig config = DefaultConfigLoader.load();

        // 1. Costruisce una mappa status -> category
        Map<String, String> statusCategoryMap = config.getStatus().stream()
                .collect(Collectors.toMap(
                        s -> s.getName().trim(),
                        s -> s.getCategory().trim()
                ));

        for (var wfDto : config.getWorkflow()) {
            String workflowName = wfDto.getName().trim();

            String initialStatusName = wfDto.getInitialStatus().trim();
            Status initialStatus = statusRepository.findByTenantIdAndName(tenant.getId(), initialStatusName)
                    .orElseThrow(() -> new ApiException(STATUS_NOT_FOUND + initialStatusName));

            // 2. Crea o recupera il Workflow
            Workflow workflow = workflowRepository
                    .findByTenantIdAndName(tenant.getId(), workflowName)
                    .orElseGet(() -> {
                        Workflow w = new Workflow();
                        w.setTenant(tenant);
                        w.setScope(ScopeType.GLOBAL);
                        w.setName(workflowName);
                        w.setDefaultWorkflow(true);
                        w.setInitialStatus(initialStatus);
                        return workflowRepository.save(w);
                    });

            for (var transitionDto : wfDto.getTransitions()) {
                String fromName = transitionDto.getFromStatus().trim();
                String toName = transitionDto.getToStatus().trim();

                // Recupera lo Status (già inizializzato dallo StatusInitializer)
                Status fromStatus = statusRepository.findByTenantIdAndName(tenant.getId(), fromName)
                        .orElseThrow(() -> new ApiException(STATUS_NOT_FOUND + fromName));
                Status toStatus = statusRepository.findByTenantIdAndName(tenant.getId(), toName)
                        .orElseThrow(() -> new ApiException(STATUS_NOT_FOUND + toName));

                // Recupera o crea WorkflowStatus (FROM)
                WorkflowStatus fromWfStatus = workflowStatusRepository.findByWorkflowAndStatus(workflow, fromStatus)
                        .orElseGet(() -> {
                            WorkflowStatus ws = new WorkflowStatus();
                            ws.setWorkflow(workflow);
                            ws.setStatus(fromStatus);
                            ws.setStatusCategory(StatusCategory.valueOf(statusCategoryMap.get(fromName)));
                            if (fromStatus.getId().equals(initialStatus.getId())) {
                                ws.setInitial(true);
                            }
                            return workflowStatusRepository.save(ws);
                        });

                // Recupera o crea WorkflowStatus (TO)
                WorkflowStatus toWfStatus = workflowStatusRepository.findByWorkflowAndStatus(workflow, toStatus)
                        .orElseGet(() -> {
                            WorkflowStatus ws = new WorkflowStatus();
                            ws.setWorkflow(workflow);
                            ws.setStatus(toStatus);
                            ws.setStatusCategory(StatusCategory.valueOf(statusCategoryMap.get(toName)));
                            return workflowStatusRepository.save(ws);
                        });

                // 4. Crea la Transition
                if (transitionRepository.findByWorkflowAndFromStatusAndToStatus(workflow, fromWfStatus, toWfStatus).isEmpty()) {
                    Transition t = new Transition();
                    t.setWorkflow(workflow);
                    t.setFromStatus(fromWfStatus);
                    t.setToStatus(toWfStatus);
                    transitionRepository.save(t);
                }
            }

            for (var metaDto : config.getWorkflowNodes()) {

                Double posX = metaDto.getPositionX();
                Double posY = metaDto.getPositionY();
                String nodeId = metaDto.getNodeId().trim();

                Status status = statusRepository.findByTenantIdAndName(tenant.getId(), nodeId).
                        orElseThrow(() -> new ApiException(STATUS_NOT_FOUND + nodeId));

                WorkflowNode metadata = new WorkflowNode();
                metadata.setWorkflow(workflow);
                metadata.setTenant(tenant);
                metadata.setPositionX(posX);
                metadata.setPositionY(posY);
                WorkflowStatus workflowStatus = workflowStatusRepository.findByWorkflowAndStatus(workflow, status)
                        .orElseThrow(() -> new ApiException(WORKFLOW_STATUS_NOT_FOUND + status.getName()));
                metadata.setWorkflowStatus(workflowStatus);

                workflowNodeRepository.save(metadata);

            }

            for (var metaDto : config.getWorkflowEdges()) {
                Status sourceStatus = statusRepository.findByTenantIdAndName(tenant.getId(), metaDto.getSourceId().trim()).
                        orElseThrow(() -> new ApiException(STATUS_NOT_FOUND + metaDto.getSourceId()));
                Status targetStatus = statusRepository.findByTenantIdAndName(tenant.getId(), metaDto.getTargetId().trim()).
                        orElseThrow(() -> new ApiException(STATUS_NOT_FOUND + metaDto.getSourceId()));

                WorkflowEdge metadata = new WorkflowEdge();
                metadata.setWorkflow(workflow);
                metadata.setTenant(tenant);
                metadata.setSourceId(sourceStatus.getId());
                metadata.setSourcePosition(metaDto.getSourcePosition());
                metadata.setTargetId(targetStatus.getId());
                metadata.setTargetPosition(metaDto.getTargetPosition());
                Transition transition = transitionRepository.findByWorkflowAndFromStatusAndToStatus(
                        workflow,
                        workflowStatusRepository.findByWorkflowAndStatus(workflow, sourceStatus)
                                .orElseThrow(() -> new ApiException(WORKFLOW_STATUS_NOT_FOUND + sourceStatus.getName())),
                        workflowStatusRepository.findByWorkflowAndStatus(workflow, targetStatus)
                                .orElseThrow(() -> new ApiException(WORKFLOW_STATUS_NOT_FOUND + targetStatus.getName()))
                ).orElseThrow(() -> new ApiException("Transition not found from " + sourceStatus.getName() + " to " + targetStatus.getName()));
                metadata.setTransition(transition);

                workflowEdgeRepository.save(metadata);

            }

        }
    }
}
package com.example.demo.service;

import com.example.demo.dto.TransitionCreateDto;
import com.example.demo.dto.TransitionViewDto;
import com.example.demo.entity.Status;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Transition;
import com.example.demo.entity.WorkflowStatus;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.TransitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class TransitionService {

    private final TransitionRepository transitionRepository;

    private final StatusLookup statusLookup;
    private final WorkflowStatusLookup workflowStatusLookup;
    private final WorkflowService workflowService;

    private final DtoMapperFacade dtoMapper;

    public TransitionViewDto createTransition(Tenant tenant, TransitionCreateDto dto) {

        Status fromStatus = statusLookup.getById(tenant, dto.fromStatusId());
        if (!fromStatus.getTenant().equals(tenant)) throw new ApiException("Illegal tenant");

        Status toStatus = statusLookup.getById(tenant, dto.toStatusId());
        if (!toStatus.getTenant().equals(tenant)) throw new ApiException("Illegal tenant");

        WorkflowStatus from = workflowStatusLookup.getById(fromStatus.getId(), tenant);

        WorkflowStatus to = workflowStatusLookup.getById(toStatus.getId(), tenant);

        // Verifica duplicato: stessa transizione già esistente nello stesso progetto
        if (transitionRepository.existsByFromStatusAndToStatus(from, to))
            throw new ApiException("La transizione è già presente.");


        Transition transition = new Transition();
        transition.setFromStatus(from);
        transition.setToStatus(to);
        transitionRepository.save(transition);

        return dtoMapper.toTransitionViewDto(transition);
    }


    @Transactional(readOnly = true)
    public List<Transition> getOutgoingTransitions(Tenant tenant, Long statusId) {
        WorkflowStatus status = workflowStatusLookup.getById(statusId, tenant);
        return transitionRepository.findByFromStatusAndTenant(status, tenant);
    }

    @Transactional(readOnly = true)
    public List<Transition> getIncomingTransitions(Tenant tenant, Long statusId) {
        WorkflowStatus status = workflowStatusLookup.getById(statusId, tenant);
        return transitionRepository.findByToStatusAndTenant(status, tenant);
    }

    public void deleteTransition(Tenant tenant, Long id) {
        Transition transition = transitionRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException("Transition not found"));

        if (transition.getWorkflow().isDefaultWorkflow()) throw new ApiException("Default Workflow transition cannot be deleted");

        // Prima rimuovi le ExecutorPermissions associate
        workflowService.removeExecutorPermissionsForTransition(tenant, id);
        
        // Poi elimina la Transition
        transitionRepository.deleteById(id);
    }
}

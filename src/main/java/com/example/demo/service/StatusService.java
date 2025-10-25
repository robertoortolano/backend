package com.example.demo.service;

import com.example.demo.dto.StatusCreateDto;
import com.example.demo.dto.StatusDetailDto;
import com.example.demo.dto.StatusViewDto;
import com.example.demo.entity.Status;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Workflow;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.StatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class StatusService {

    private final StatusRepository statusRepository;

    private final WorkflowStatusLookup workflowStatusLookup;
    private final StatusLookup statusLookup;

    private final DtoMapperFacade dtoMapper;


    public StatusViewDto createStatus(StatusCreateDto statusCreateDto, Tenant tenant) {
        if (statusRepository.existsByNameAndTenant(statusCreateDto.name(), tenant)) {
            throw new ApiException("Uno status con questo nome esiste già nel progetto.");
        }

        Status status = new Status();
        status.setName(statusCreateDto.name());
        status.setTenant(tenant);

        Status saved = statusRepository.save(status);

        return dtoMapper.toStatusViewDto(saved);
    }

    @Transactional(readOnly = true)
    public StatusViewDto getById(Tenant tenant, Long id) {
        return dtoMapper.toStatusViewDto(statusLookup.getById(tenant, id));
    }

    @Transactional(readOnly = true)
    public List<StatusViewDto> getAllStatuses(Tenant tenant) {

        List<Status> statuses = statusRepository.findAllByTenant(tenant);

        return dtoMapper.toStatusViewDtos(statuses);
    }

    public StatusViewDto updateStatus(Tenant tenant, Long id, StatusViewDto dto) {
        Status status = statusRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException("Status not found"));

        if (status.isDefaultStatus())
            throw new ApiException("Default status cannot be edited");

        status.setName(dto.name());
        Status saved =  statusRepository.save(status);

        return dtoMapper.toStatusViewDto(saved);
    }

    @Transactional(readOnly = true)
    public StatusDetailDto getStatusDetails(Tenant tenant, Long statusId) {
        Status status = statusRepository.findById(statusId)
                .orElseThrow(() -> new ApiException("Status not found with ID: " + statusId));

        List<Workflow> workflows = workflowStatusLookup.getDistinctWorkflowsByStatus(status, tenant);

        return dtoMapper.toStatusDetailDto(status, workflows);
    }

    @Transactional(readOnly = true)
    public List<StatusDetailDto> getAllStatusDetails(Tenant tenant) {
        List<Status> statuses = statusRepository.findAllByTenant(tenant);

        return statuses.stream()
                .map(status -> {
                    List<Workflow> workflows = workflowStatusLookup.getDistinctWorkflowsByStatus(status, tenant);
                    return dtoMapper.toStatusDetailDto(status, workflows);
                })
                .toList();
    }

    public void deleteStatus(Tenant tenant, Long statusId) {
        Status status = statusRepository.findByIdAndTenant(statusId, tenant)
                .orElseThrow(() -> new ApiException("Status not found"));

        if (status.isDefaultStatus()) {
            throw new ApiException("Default status cannot be deleted");
        }

        // Check if status is used in any workflow
        List<Workflow> workflows = workflowStatusLookup.getDistinctWorkflowsByStatus(status, tenant);
        if (!workflows.isEmpty()) {
            throw new ApiException("Status is used in workflows and cannot be deleted");
        }

        statusRepository.delete(status);
    }

}
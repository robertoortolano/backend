package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.WorkflowStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkflowStatusService {

    private final WorkflowStatusRepository workflowStatusRepository;

    private final StatusLookup statusLookup;

    public boolean isStatusUsedInAnyWorkflow(Tenant tenant, Long statusId) {
        Status status = statusLookup.getById(tenant, statusId);

        if (!status.getTenant().equals(tenant)) throw new ApiException("Illegal tenant");

        return workflowStatusRepository.existsByStatusId(statusId);
    }

    public Set<Project> findProjectsUsingStatus(Long statusId, Tenant tenant) {
        return workflowStatusRepository.findProjectsUsingStatus(statusId, tenant.getId());
    }

}

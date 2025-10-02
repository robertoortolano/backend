package com.example.demo.service;

import com.example.demo.entity.Status;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.Workflow;
import com.example.demo.entity.WorkflowStatus;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.WorkflowStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkflowStatusLookup {

    private final WorkflowStatusRepository workflowStatusRepository;

    List<Workflow> getDistinctWorkflowsByStatus(Status status, Tenant tenant) {
        return workflowStatusRepository.findDistinctWorkflowsByStatusAndTenant(status, tenant);
    }

    WorkflowStatus getById(Long id, Tenant tenant) {
        WorkflowStatus workflowStatus = workflowStatusRepository.findById(id)
                .orElseThrow(() -> new ApiException("Workflow Status not found"));
        if (!workflowStatus.getStatus().getTenant().equals(tenant)) throw new ApiException("Illegal tenant");
        return workflowStatus;
    }
}

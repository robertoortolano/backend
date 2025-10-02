package com.example.demo.service;

import com.example.demo.entity.Project;
import com.example.demo.entity.Tenant;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectLookup {

    private final ProjectRepository projectRepository;

    public Project getById(Tenant tenant, Long id) {
        return projectRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException("Project not found"));
    }

}

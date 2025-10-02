package com.example.demo.service;

import com.example.demo.entity.Status;
import com.example.demo.entity.Tenant;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.StatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StatusLookup {

    private final StatusRepository statusRepository;

    public Status getById(Tenant tenant, Long id) {
        return statusRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException("Status not found"));
    }

}

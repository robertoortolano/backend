package com.example.demo.service;

import com.example.demo.entity.FieldConfiguration;
import com.example.demo.entity.Tenant;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.FieldConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FieldConfigurationLookup {

    private final FieldConfigurationRepository fieldConfigurationRepository;

    private static final String FIELDCONFIGURATION_NOT_FOUND = "FieldConfiguration not found";

    List<FieldConfiguration> getAllByField(Long fieldId, Tenant tenant) {
        return fieldConfigurationRepository.findByFieldIdAndTenant(fieldId, tenant);
    }

    public boolean existsByField(Long fieldId, Tenant tenant) {
        return fieldConfigurationRepository.existsByFieldIdAndTenant(fieldId, tenant);
    }

    List<FieldConfiguration> getAll(List<Long> configIds, Tenant tenant) {
        return fieldConfigurationRepository.findAllByIdInAndTenant(configIds, tenant);
    }

    public FieldConfiguration getById(Long id, Tenant tenant) {
        return fieldConfigurationRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException(FIELDCONFIGURATION_NOT_FOUND));
    }

}

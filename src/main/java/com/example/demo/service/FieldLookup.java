package com.example.demo.service;

import com.example.demo.entity.Field;
import com.example.demo.entity.Tenant;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.FieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FieldLookup {

    private final FieldRepository fieldRepository;

    public Field getById(Long id, Tenant tenant) {
        return fieldRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException("Field not found with id: " + id));
    }

}

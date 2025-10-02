package com.example.demo.service;

import com.example.demo.entity.FieldSet;
import com.example.demo.entity.Tenant;
import com.example.demo.exception.ApiException;
import com.example.demo.repository.FieldSetEntryRepository;
import com.example.demo.repository.FieldSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FieldSetLookup {

    private final FieldSetEntryRepository fieldSetEntryRepository;
    private final FieldSetRepository fieldSetRepository;

    private static final String FIELDSET_NOT_FOUND = "FieldSet not found";


    List<FieldSet> getAllByField(Long fieldId, Tenant tenant) {
        return fieldSetEntryRepository.findDistinctFieldSetsByFieldIdAndTenantId(fieldId, tenant.getId());
    }

    public boolean existsByFieldConfigurationFieldId(Long fieldId) {
        return fieldSetEntryRepository.existsByFieldConfiguration_Field_Id(fieldId);
    }

    public boolean isFieldConfigurationInAnyFieldSet(Long fieldConfigurationId, Tenant tenant) {
        return fieldSetEntryRepository
                .existsByFieldConfiguration_IdAndFieldSet_Tenant_Id(fieldConfigurationId, tenant.getId());
    }

    public FieldSet getFirstDefault(Tenant tenant) {
        return fieldSetRepository.findFirstByTenantAndDefaultFieldSetTrue(tenant);
    }


    public FieldSet getById(Long id, Tenant tenant) {
        return fieldSetRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException(FIELDSET_NOT_FOUND + ": " + id));
    }

}

package com.example.demo.service;

import com.example.demo.dto.FieldCreateDto;
import com.example.demo.dto.FieldViewDto;
import com.example.demo.entity.Field;
import com.example.demo.entity.FieldConfiguration;
import com.example.demo.entity.FieldSet;
import com.example.demo.entity.Tenant;
import com.example.demo.exception.ApiException;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.dto.FieldDetailDto;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FieldService {

    private final FieldRepository fieldRepository;

    private final FieldConfigurationLookup fieldConfigurationLookup;
    private final FieldSetLookup fieldSetLookup;
    private final FieldLookup fieldLookup;

    private final DtoMapperFacade dtoMapper;

    public FieldViewDto createField(FieldCreateDto dto, Tenant tenant) {
        Field field = dtoMapper.toField(dto);
        field.setTenant(tenant);
        Field saved = fieldRepository.save(field);
        return dtoMapper.toFieldViewDto(saved);
    }

    @Transactional(readOnly = true)
    public FieldViewDto getById(Long id, Tenant tenant) {
        return dtoMapper.toFieldViewDto(fieldLookup.getById(id, tenant));
    }

    @Transactional(readOnly = true)
    public FieldDetailDto getFieldDetail(Long fieldId, Tenant tenant) {
        Field field = fieldRepository.findByIdAndTenant(fieldId, tenant)
                .orElseThrow(() -> new ApiException("Campo non trovato"));

        List<FieldConfiguration> configs = fieldConfigurationLookup.getAllByField(fieldId, tenant);
        List<FieldSet> fieldSets = fieldSetLookup.getAllByField(fieldId, tenant);

        return dtoMapper.toFieldDetailDto(field, configs, fieldSets);
    }


    @Transactional(readOnly = true)
    public List<FieldViewDto> getFields(Tenant tenant) {

        return fieldRepository.findAllByTenant(tenant)
                .stream()
                .map(dtoMapper::toFieldViewDto)
                .toList();

    }

    @Transactional(readOnly = true)
    public List<FieldDetailDto> getFieldsDetails(Tenant tenant) {
        List<Field> fields = fieldRepository.findAllByTenant(tenant);

        return fields.stream()
                .map(field -> {
                    List<FieldConfiguration> configurations =
                            fieldConfigurationLookup.getAllByField(field.getId(), tenant);

                    List<FieldSet> fieldSets =
                            fieldSetLookup.getAllByField(field.getId(), tenant);

                    return dtoMapper.toFieldDetailDto(field, configurations, fieldSets);
                })
                .toList();
    }


    public FieldViewDto updateField(Tenant tenant, Long id, FieldCreateDto dto) {
        Field field = fieldRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException("Field not found with id: " + id));

        if (field.isDefaultField()) throw new ApiException("Default field cannot be edited");

        field.setName(dto.name());
        Field updated = fieldRepository.save(field);
        return dtoMapper.toFieldViewDto(updated);
    }

    public void saveAll(Tenant tenant, List<Field> fieldList) {
        if (!fieldList.stream()
                .allMatch(field -> field.getTenant().getId().equals(tenant.getId()))) {
            throw new ApiException("Almeno un field non appartiene alla tenant");
        }

        if (fieldList.stream()
                .noneMatch(Field::isDefaultField)) {
            throw new ApiException("Default field cannot be saved");
        }

        fieldRepository.saveAll(fieldList);
    }


    public void deleteField(Long id, Tenant tenant) {
        if (isFieldUsed(tenant, id)) {
            throw new ApiException("Impossibile eliminare il campo: è utilizzato in una configurazione o in un field set.");
        }
        Field field = fieldRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException("Campo non trovato con id: " + id));

        if (field.isDefaultField()) throw new ApiException("Default field cannot be deleted");

        fieldRepository.delete(field);
    }


    private boolean isFieldUsed(Tenant tenant, Long fieldId) {
        boolean usedInConfigurations = fieldConfigurationLookup.existsByField(fieldId, tenant);
        boolean usedInFieldSets = fieldSetLookup.existsByFieldConfigurationFieldId(fieldId);
        return usedInConfigurations || usedInFieldSets;
    }

}

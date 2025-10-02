package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.enums.FieldType;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.fieldtype.FieldTypeRegistry;
import com.example.demo.mapper.DtoMapperFacade;
import com.example.demo.repository.FieldConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FieldConfigurationService {

    private final FieldConfigurationRepository fieldConfigurationRepository;
    private final FieldTypeRegistry fieldTypeRegistry;

    private final FieldConfigurationLookup fieldConfigurationLookup;
    private final FieldLookup fieldLookup;
    private final FieldSetLookup fieldSetLookup;

    private final DtoMapperFacade dtoMapper;

    private static final String FIELDCONFIGURATION_NOT_FOUND = "FieldConfiguration not found";

    public FieldConfigurationViewDto createGlobalFieldConfiguration(FieldConfigurationCreateDto dto, Tenant tenant) {
        Field field = fieldLookup.getById(dto.fieldId(), tenant);

        FieldType fieldType = dto.fieldType();

        FieldConfiguration entity = new FieldConfiguration();
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setField(field);
        entity.setAlias(dto.alias());
        entity.setTenant(tenant);
        entity.setFieldType(fieldType);
        entity.setScope(ScopeType.GLOBAL); // o GLOBAL, a seconda del contesto
        entity.setDefaultFieldConfiguration(false); // oppure true, se è il default
        entity.setOptions(dtoMapper.toFieldOptionEntitySetFromCreate(dto.options()));

        FieldConfiguration fieldConfiguration = fieldConfigurationRepository.save(entity);
        return dtoMapper.toFieldConfigurationViewDto(fieldConfiguration);
    }

    @Transactional(readOnly = true)
    public List<FieldConfigurationViewDto> getAllGlobalFieldConfigurations(Tenant tenant) {

        List<FieldConfiguration> configurations = fieldConfigurationRepository.findByTenantAndScope(tenant, ScopeType.GLOBAL);
        return dtoMapper.toFieldConfigurationViewDtos(configurations);
    }

    @Transactional(readOnly = true)
    public FieldConfigurationViewDto getById(Long id, Tenant tenant) {
        return dtoMapper.toFieldConfigurationViewDto(fieldConfigurationLookup.getById(id, tenant));
    }

    public FieldConfigurationViewDto updateConfiguration(Tenant tenant, Long id, FieldConfigurationUpdateDto dto) {
        FieldConfiguration config = fieldConfigurationRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException(FIELDCONFIGURATION_NOT_FOUND));

        if (config.isDefaultFieldConfiguration()) throw new ApiException("Default Field Configuration cannot be edited");

        config.setName(dto.name());
        config.setDescription(dto.description());
        FieldType fieldType = dto.fieldType();
        config.setFieldType(fieldType);
        config.setAlias(dto.alias());

        // Verifica se il tipo supporta options tramite il registry
        boolean supportsOptions = fieldTypeRegistry.getDescriptor(fieldType).isSupportsOptions();

        if (supportsOptions) {
            if (dto.options() != null) {
                config.getOptions().clear();

                Set<FieldOption> options = dtoMapper.toFieldOptionEntitySetFromUpdate(dto.options());

                config.getOptions().addAll(options);
            } else {
                config.getOptions().clear();
            }
        } else {
            // Se non supporta options, assicurati che sia vuoto
            config.getOptions().clear();
        }

        if (!config.getField().getId().equals(dto.fieldId())) {
            config.setField(fieldLookup.getById(dto.fieldId(), tenant));
        }

        FieldConfiguration saved = fieldConfigurationRepository.save(config);
        return dtoMapper.toFieldConfigurationViewDtos(List.of(saved)).getFirst();
    }

    public void deleteFieldConfiguration(Tenant tenant, Long id) {
        FieldConfiguration fieldConfiguration = fieldConfigurationRepository.findByIdAndTenant(id, tenant)
                .orElseThrow(() -> new ApiException("FieldConfiguration not found with id: " + id));

        if (fieldConfiguration.isDefaultFieldConfiguration()) throw new ApiException("Default Field Configuration cannot be deleted");

        fieldConfigurationRepository.deleteByIdAndTenant(id, tenant);
    }

    public boolean isFieldInAnyFieldConfiguration(Tenant tenant, Long fieldId) {
        return fieldConfigurationRepository.existsByFieldIdAndTenant(fieldId, tenant);
    }

    public Set<Project> getProjectsUsingField(Tenant tenant, Long fieldId) {
        List<FieldConfiguration> fieldConfigurations = fieldConfigurationRepository
                .findByFieldIdAndProjectIsNotNullAndTenant(fieldId, tenant);

        return fieldConfigurations.stream()
                .map(FieldConfiguration::getProject)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public boolean isFieldConfigurationInAnyFieldSet(Long fieldConfigurationId, Tenant tenant) {
        return fieldSetLookup.isFieldConfigurationInAnyFieldSet(fieldConfigurationId, tenant);
    }

}

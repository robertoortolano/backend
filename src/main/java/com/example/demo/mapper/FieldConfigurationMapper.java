package com.example.demo.mapper;

import com.example.demo.dto.*;
import com.example.demo.entity.*;
import com.example.demo.enums.ScopeType;
import com.example.demo.exception.ApiException;
import com.example.demo.fieldtype.FieldTypeRegistry;
import com.example.demo.repository.FieldRepository;
import com.example.demo.repository.FieldSetEntryRepository;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {FieldOptionMapper.class})
public abstract class FieldConfigurationMapper {

    protected FieldTypeRegistry fieldTypeRegistry;
    protected FieldOptionMapper fieldOptionMapper;
    protected FieldSetEntryRepository fieldSetEntryRepository;

    @Autowired
    public void setDependencies(FieldTypeRegistry fieldTypeRegistry, FieldOptionMapper fieldOptionMapper, FieldSetEntryRepository fieldSetEntryRepository) {
        this.fieldTypeRegistry = fieldTypeRegistry;
        this.fieldOptionMapper = fieldOptionMapper;
        this.fieldSetEntryRepository = fieldSetEntryRepository;
    }


    //FieldConfigurationMapper

    @Mapping(source = "field.name", target = "fieldName")
    @Mapping(target = "fieldType", expression = "java(fieldTypeRegistry.getDescriptor(entity.getFieldType()))")
    public abstract FieldConfigurationDto toDto(FieldConfiguration entity);

    public List<FieldConfigurationDto> toDtos(List<FieldConfiguration> entities) {
        if (entities == null) return Collections.emptyList();
        return entities.stream().map(this::toDto).toList();
    }


    @Mapping(target = "id", source = "entity.id")
    @Mapping(target = "description", source = "entity.description")
    @Mapping(target = "fieldId", source = "entity.field.id")
    @Mapping(target = "fieldName", source = "entity.field.name")
    @Mapping(target = "defaultFieldConfiguration", source = "entity.defaultFieldConfiguration")
    @Mapping(target = "fieldType", expression = "java(fieldTypeRegistry.getDescriptor(entity.getFieldType()))")
    @Mapping(target = "scope", source = "entity.scope")
    @Mapping(target = "options", expression = "java(fieldOptionMapper.toViewDtoSet(entity.getOptions()))")
    @Mapping(target = "usedInFieldSets", expression = "java(mapUsedFieldSets(entity))")
    public abstract FieldConfigurationViewDto toViewDto(FieldConfiguration entity);

    public List<FieldConfigurationViewDto> toViewDtos(List<FieldConfiguration> entities) {
        if (entities == null) return Collections.emptyList();
        return entities.stream().map(this::toViewDto).toList();
    }

    protected List<SimpleFieldSetDto> mapUsedFieldSets(FieldConfiguration entity) {
        return fieldSetEntryRepository.findByFieldConfigurationId(entity.getId()).stream()
                .map(entry -> new SimpleFieldSetDto(entry.getFieldSet().getId(), entry.getFieldSet().getName()))
                .distinct()
                .toList();
    }



    public FieldConfiguration toFieldConfigurationEntity(
            FieldConfigurationCreateDto dto,
            FieldSet fieldSet,
            FieldRepository fieldRepository,
            ScopeType scopeType
    ) {
        Field field = fieldRepository.findById(dto.fieldId())
                .orElseThrow(() -> new ApiException("Field non trovato: id " + dto.fieldId()));

        FieldConfiguration config = new FieldConfiguration();
        config.setField(field);
        config.setDescription(dto.description());
        config.setFieldType(dto.fieldType());
        config.setDefaultFieldConfiguration(false); // creato da utente
        config.setScope(scopeType);
        config.setTenant(fieldSet.getTenant());

        if (dto.options() != null && !dto.options().isEmpty()) {
            Set<FieldOption> options = dto.options().stream()
                    .map(optDto -> {
                        FieldOption opt = new FieldOption();
                        opt.setLabel(optDto.label());
                        opt.setValue(optDto.value());
                        return opt;
                    })
                    .collect(Collectors.toSet());
            config.setOptions(options);
        } else {
            config.setOptions(new HashSet<>());
        }

        return config;
    }

}



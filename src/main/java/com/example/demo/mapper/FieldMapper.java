package com.example.demo.mapper;

import com.example.demo.dto.FieldCreateDto;
import com.example.demo.dto.FieldDetailDto;
import com.example.demo.dto.FieldViewDto;
import com.example.demo.entity.Field;
import com.example.demo.entity.FieldConfiguration;
import com.example.demo.entity.FieldSet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {FieldConfigurationMapper.class, FieldSetMapper.class})
public interface FieldMapper {

    FieldViewDto toDto(Field field);

    // Mapping da RequestDto a Entity
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "defaultField", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    Field toEntity(FieldCreateDto dto);

    @Mapping(target = "fieldConfigurations", source = "fieldConfigurations")
    @Mapping(target = "fieldSets", source = "fieldSets")
    FieldDetailDto toDetailDto(Field field, List<FieldConfiguration> fieldConfigurations, List<FieldSet> fieldSets);

}
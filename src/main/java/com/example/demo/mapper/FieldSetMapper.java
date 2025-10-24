package com.example.demo.mapper;

import com.example.demo.dto.FieldSetViewDto;
import com.example.demo.entity.FieldSet;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", uses = FieldSetEntryMapper.class)
public interface FieldSetMapper {

    @Mapping(source = "fieldSetEntries", target = "fieldSetEntries")
    FieldSetViewDto toViewDto(FieldSet fieldSet);

    List<FieldSetViewDto> toViewDtos(List<FieldSet> fieldSets);

    // Da gestire nel service: FieldSetCreateDto → FieldSet (serve accesso ai FieldConfiguration)
}

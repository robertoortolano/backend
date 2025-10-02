package com.example.demo.mapper;

import com.example.demo.dto.FieldSetViewDto;
import com.example.demo.entity.FieldSet;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = FieldSetEntryMapper.class)
public interface FieldSetMapper {

    @Mapping(source = "fieldSetEntries", target = "fieldSetEntries")
    FieldSetViewDto toViewDto(FieldSet fieldSet);

    // Da gestire nel service: FieldSetCreateDto → FieldSet (serve accesso ai FieldConfiguration)
}

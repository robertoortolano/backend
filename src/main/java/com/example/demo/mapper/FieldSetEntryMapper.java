package com.example.demo.mapper;

import com.example.demo.dto.FieldSetEntryViewDto;
import com.example.demo.entity.FieldSetEntry;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", uses = FieldConfigurationMapper.class)
public interface FieldSetEntryMapper {

    @Mapping(source = "fieldConfiguration", target = "fieldConfiguration")
    FieldSetEntryViewDto toViewDto(FieldSetEntry entry);

    List<FieldSetEntryViewDto> toViewDtoList(List<FieldSetEntry> entries);

}

package com.example.demo.mapper;

import com.example.demo.dto.*;
import com.example.demo.entity.ItemTypeConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {
        ItemTypeMapper.class,
        WorkflowMapper.class,
        FieldSetMapper.class
})
public interface ItemTypeConfigurationMapper {

    @Mapping(source = "itemType", target = "itemType")
    @Mapping(source = "workflow", target = "workflow")
    @Mapping(source = "fieldSet", target = "fieldSet")
    ItemTypeConfigurationViewDto toViewDto(ItemTypeConfiguration entity);
}

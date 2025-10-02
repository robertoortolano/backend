package com.example.demo.mapper;

import com.example.demo.dto.*;
import com.example.demo.entity.ItemTypeSet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {
        ProjectSummaryMapper.class,
        ItemTypeConfigurationMapper.class
})
public interface ItemTypeSetMapper {

    @Mapping(source = "projectsAssociation", target = "projectsAssociation")
    @Mapping(source = "itemTypeConfigurations", target = "itemTypeConfigurations")
    ItemTypeSetViewDto toViewDto(ItemTypeSet entity);

    // MapStruct mapperà automaticamente Set → List se i mapper usati restituiscono liste o set (usa conversioni java.util)
}

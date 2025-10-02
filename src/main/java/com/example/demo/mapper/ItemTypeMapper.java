package com.example.demo.mapper;

import com.example.demo.dto.ItemTypeCreateDto;
import com.example.demo.dto.ItemTypeViewDto;
import com.example.demo.entity.ItemType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ItemTypeMapper {

    // Mappa da DTO di richiesta a Entity (per creare/modificare)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    @Mapping(target = "defaultItemType", ignore = true)
    ItemType toEntity(ItemTypeCreateDto dto);

    // Mappa da Entity a DTO di risposta (per leggere)
    @Mapping(source = "defaultItemType", target = "defaultItemType")
    ItemTypeViewDto toDto(ItemType entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    @Mapping(target = "defaultItemType", ignore = true)
    void updateItemTypeFromDto(ItemTypeCreateDto dto, @MappingTarget ItemType entity);

}


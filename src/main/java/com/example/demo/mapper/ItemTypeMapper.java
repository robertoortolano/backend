package com.example.demo.mapper;

import com.example.demo.dto.ItemTypeCreateDto;
import com.example.demo.dto.ItemTypeDetailDto;
import com.example.demo.dto.ItemTypeViewDto;
import com.example.demo.dto.ItemTypeConfigurationViewDto;
import com.example.demo.entity.ItemType;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.enums.ItemTypeCategory;
import com.example.demo.enums.ScopeType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    @Mapping(target = "itemTypeConfigurations", source = "itemTypeConfigurations", qualifiedByName = "mapItemTypeConfigurations")
    ItemTypeDetailDto toDetailDto(ItemType itemType, List<ItemTypeConfiguration> itemTypeConfigurations);
    
    @Named("mapItemTypeConfigurations")
    default List<ItemTypeConfigurationViewDto> mapItemTypeConfigurations(List<ItemTypeConfiguration> configs) {
        if (configs == null) return null;
        return configs.stream()
                .map(config -> new ItemTypeConfigurationViewDto(
                        config.getId(),
                        new ItemTypeViewDto(config.getItemType().getId(), config.getItemType().getName(), config.getItemType().isDefaultItemType()),
                        null, // category - non necessario per il controllo di eliminazione
                        false, // defaultItemTypeConfiguration - non necessario per il controllo di eliminazione
                        null, // scope - non necessario per il controllo di eliminazione
                        null, // workers - non necessario per il controllo di eliminazione
                        null, // workflow - non necessario per il controllo di eliminazione
                        null  // fieldSet - non necessario per il controllo di eliminazione
                ))
                .collect(Collectors.toList());
    }

}


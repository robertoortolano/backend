package com.example.demo.mapper;

import com.example.demo.dto.*;
import com.example.demo.entity.ItemTypeConfiguration;
import com.example.demo.entity.ItemTypeSet;
import com.example.demo.entity.Transition;
import com.example.demo.entity.Workflow;
import com.example.demo.enums.ItemTypeCategory;
import com.example.demo.enums.ScopeType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {
        WorkflowStatusMapper.class,
        StatusMapper.class,
        Transition.class
})
public interface WorkflowMapper {

    @Mapping(target = "workflowNodes", ignore = true)
    @Mapping(target = "workflowEdges", ignore = true)
    WorkflowViewDto toViewDto(Workflow workflow);
    List<WorkflowViewDto> toViewDtos(List<Workflow> workflows);

    @Mapping(target = "usedInItemTypeConfigurations", source = "usedInItemTypeSets", qualifiedByName = "mapItemTypeSetsToConfigurations")
    WorkflowDetailDto toDetailDto(Workflow workflow, List<ItemTypeSet> usedInItemTypeSets);
    
    @Named("mapItemTypeSetsToConfigurations")
    default List<ItemTypeConfigurationViewDto> mapItemTypeSetsToConfigurations(List<ItemTypeSet> itemTypeSets) {
        if (itemTypeSets == null) return null;
        return itemTypeSets.stream()
                .map(itemTypeSet -> new ItemTypeConfigurationViewDto(
                        itemTypeSet.getId(), // Usiamo l'ID dell'ItemTypeSet
                        new ItemTypeViewDto(itemTypeSet.getId(), itemTypeSet.getName(), itemTypeSet.isDefaultItemTypeSet()),
                        null, // category - non necessario per il popup
                        false, // defaultItemTypeConfiguration - non necessario per il popup
                        null, // scope - non necessario per il popup
                        null, // workers - non necessario per il popup
                        null, // workflow - non necessario per il popup
                        null  // fieldSet - non necessario per il popup
                ))
                .collect(Collectors.toList());
    }
}

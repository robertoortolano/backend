package com.example.demo.metadata;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WorkflowMetaMapper {

    WorkflowMetaMapper INSTANCE = Mappers.getMapper(WorkflowMetaMapper.class);

    // ---- Node ----
    @Mapping(target = "statusId", source = "workflowStatus.status.id")
    WorkflowNodeDto toDto(WorkflowNode entity);

    List<WorkflowNodeDto> toNodeDtos(List<WorkflowNode> entities);

    @Mapping(target = "id", ignore = true)        // id generato dal DB
    @Mapping(target = "tenant", ignore = true)
    @Mapping(target = "workflow", ignore = true)
    @Mapping(target = "workflowStatus", ignore = true)
    WorkflowNode toEntity(WorkflowNodeDto dto);

    // ---- Edge ----
    @Mapping(target = "transitionId", source = "transition.id")
    @Mapping(target = "transitionTempId", ignore = true)
    WorkflowEdgeDto toDto(WorkflowEdge edge);

    List<WorkflowEdgeDto> toEdgeDtos(List<WorkflowEdge> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenant", ignore = true)
    @Mapping(target = "workflow", ignore = true)
    @Mapping(target = "transition", ignore = true)
    WorkflowEdge toEntity(WorkflowEdgeDto dto);

}


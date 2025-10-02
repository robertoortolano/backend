package com.example.demo.mapper;

import com.example.demo.dto.*;
import com.example.demo.entity.Transition;
import com.example.demo.entity.Workflow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

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
}

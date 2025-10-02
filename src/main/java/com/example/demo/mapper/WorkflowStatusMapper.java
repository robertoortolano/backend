package com.example.demo.mapper;

import com.example.demo.dto.WorkflowStatusViewDto;
import com.example.demo.entity.WorkflowStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {
        StatusMapper.class,
        TransitionMapper.class
})
public interface WorkflowStatusMapper {

    @Mapping(source = "workflow.id", target = "workflowId")
    @Mapping(source = "workflow.name", target = "workflowName")
    WorkflowStatusViewDto toViewDto(WorkflowStatus entity);

    List<WorkflowStatusViewDto> toViewDtos(List<WorkflowStatus> workflowStatuses);
}

package com.example.demo.mapper;

import com.example.demo.dto.StatusDetailDto;
import com.example.demo.dto.StatusViewDto;
import com.example.demo.dto.WorkflowSimpleDto;
import com.example.demo.entity.Status;
import com.example.demo.entity.Workflow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface StatusMapper {

    StatusViewDto toViewDto(Status status);

    List<StatusViewDto> toViewDtos(List<Status> status);

    WorkflowSimpleDto workflowToWorkflowSimpleDto(Workflow workflow);

    @Mapping(source = "status.id", target = "id")
    @Mapping(source = "status.name", target = "name")
    @Mapping(source = "status.defaultStatus", target = "defaultStatus")
    @Mapping(source = "workflows", target = "workflows", qualifiedByName = "mapWorkflows")
    StatusDetailDto toStatusDetailDto(Status status, List<Workflow> workflows);
    
    @Named("mapWorkflows")
    default List<WorkflowSimpleDto> mapWorkflows(List<Workflow> workflows) {
        if (workflows == null) return null;
        return workflows.stream()
                .map(this::workflowToWorkflowSimpleDto)
                .collect(Collectors.toList());
    }
}

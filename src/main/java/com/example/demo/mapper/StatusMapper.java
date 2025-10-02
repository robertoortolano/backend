package com.example.demo.mapper;

import com.example.demo.dto.StatusDetailDto;
import com.example.demo.dto.StatusViewDto;
import com.example.demo.dto.WorkflowSimpleDto;
import com.example.demo.entity.Status;
import com.example.demo.entity.Workflow;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface StatusMapper {

    StatusViewDto toViewDto(Status status);

    List<StatusViewDto> toViewDtos(List<Status> status);

    WorkflowSimpleDto workflowToWorkflowSimpleDto(Workflow workflow);

    StatusDetailDto toStatusDetailDto(Status status, List<Workflow> workflows);
}

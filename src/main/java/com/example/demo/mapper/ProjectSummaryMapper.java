package com.example.demo.mapper;

import com.example.demo.dto.ProjectSummaryDto;
import com.example.demo.entity.Project;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProjectSummaryMapper {

    @Mapping(source = "projectKey", target = "projectKey")
    ProjectSummaryDto toSummaryDto(Project project);

}

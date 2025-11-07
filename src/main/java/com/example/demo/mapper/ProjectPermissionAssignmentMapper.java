package com.example.demo.mapper;

import com.example.demo.dto.ProjectPermissionAssignmentDto;
import com.example.demo.entity.ProjectPermissionAssignment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {PermissionAssignmentMapper.class})
public interface ProjectPermissionAssignmentMapper {
    
    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "itemTypeSetId", source = "itemTypeSet.id")
    ProjectPermissionAssignmentDto toDto(ProjectPermissionAssignment entity);
    
    List<ProjectPermissionAssignmentDto> toDtoList(List<ProjectPermissionAssignment> entities);
    
}


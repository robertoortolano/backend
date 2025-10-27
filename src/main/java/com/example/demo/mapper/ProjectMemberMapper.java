package com.example.demo.mapper;

import com.example.demo.dto.ProjectMemberDto;
import com.example.demo.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProjectMemberMapper {

    @Mapping(source = "id", target = "userId")
    @Mapping(target = "roleName", ignore = true)
    @Mapping(target = "isTenantAdmin", ignore = true)
    ProjectMemberDto toProjectMemberDto(User user);
}


















